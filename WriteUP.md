# Working with AI on this project

## 1. What I asked the AI to do, and what I decided myself

I used the AI for implementation and for laying out options. The architectural
calls were mine.

The clearest example is the scheduling core. I asked how to pick up due jobs and
got three viable answers back:

- **Quartz** — mature, batteries included, but it brings its own schema, its own
  vocabulary, and a lot of machinery I would not be able to explain line by line.
- **Simple in-memory scheduling** — a `ScheduledExecutorService` per job. Precise
  and trivial to write, but it dies with the process and cannot run on more than
  one instance without firing everything twice.
- **Database polling with `FOR UPDATE SKIP LOCKED`** — jobs live in Postgres,
  each instance claims a disjoint slice of the due rows.

I chose the third. It is the closest to how this would actually be built in
production: state survives restarts, horizontal scaling works without a leader
election or a distributed lock, and Postgres itself arbitrates the contention.
It is also the option I understand end to end, which matters more than the option
with the most features.

Beyond that I decided the data model first — `cron_jobs` before any scheduling
logic — on the view that a scheduler is really a state machine over a table. I
also chose to advance `next_run_at` at claim time, to validate payloads at write
time rather than fire time, and to keep run history free of a foreign key so it
outlives the jobs it describes.

I wrote the prompts, the sequencing, and the review. The AI wrote most of the
Java.

## 2. Where I overrode or threw away the AI's output

**The executor and worker model — discarded and rebuilt.**

The AI's first version executed jobs inline, in the polling loop. It looked
correct and the tests passed, and it had even added a `pool-size` property with a
comment claiming one slow job would not stall the others.

That claim was wrong. There is only one `@Scheduled` method and `fixedDelay` is
non-reentrant, so raising the scheduler pool size changed nothing — every job
still ran serially on a single thread. When I pushed on it, the logs confirmed
it: two jobs executing back to back on the same `scheduling-1` thread. A single
slow HTTP call would have blocked every other job *and* the next poll.

I threw that design out and had it rebuilt with a real worker pool: polling stays
serial so claims can never overlap, but jobs are dispatched to a bounded
`ThreadPoolTaskExecutor`. I asked for proof rather than assurances — four jobs
against a deliberately slow endpoint, which then completed at the same
millisecond on four different threads instead of taking four times as long.

The lesson I took: the AI is good at producing code that compiles and reads
well, and much weaker at noticing that a config knob it just added does nothing.
Plausible-looking concurrency code is the place to be most suspicious.
## 3. The biggest trade-offs

**Database polling over Quartz or in-memory scheduling.** Covered above. The cost
is that I own the correctness problems Quartz would have solved for me —
claiming, missed runs, overlap — and that the poll interval bounds precision (a
job is up to 5 seconds late, never early). The benefit is an architecture I can
reason about and scale by adding instances.

**At-most-once over at-least-once delivery.** `next_run_at` advances when the job
is claimed, before it executes. A crash mid-execution therefore *skips* that run
rather than repeating it. The alternative — advancing on completion — gives
at-least-once and risks sending the same email twice. Neither is strictly
correct: exactly-once is not reachable by the scheduler alone, because a crash
between performing a side effect and recording it is indistinguishable from a
crash before it. I chose the failure that is quieter for email, and noted that
HTTP jobs could safely take the other side with an idempotency key.

**`jsonb` payload over per-type columns.** An email job needs
`to`/`subject`/`body`; an HTTP job needs `url`/`method`/`headers`/`body`. They
share no fields, so typed columns would leave every row half-null and require a
migration per new job type. The cost is that the database cannot validate the
shape, so the application does it at create time by handing the payload to the
executor that will run it. I considered separate tables per job type and rejected
it as too much structure for two types.

## 4. What's missing, and what I'd do with another day

**Push jobs onto Kafka instead of executing them in-process.** This is the change
I would make first, because it is what actually fixes the at-most-once problem.

Today the poller claims a due job and hands it straight to an in-memory worker
pool. That queue is the weak point: it is not durable, so anything queued or
in-flight when the process dies is gone. The schedule has already advanced, so
nothing re-claims it — the run is skipped with no record in either table.

Instead, polling would claim the due jobs and publish them to a Kafka topic, and
a separate pool of consumers would do the work. A published message is durable.
If a consumer dies mid-job, the offset was never committed, so the message is
redelivered to another consumer rather than lost. The run stops depending on one
process staying alive between claim and completion.

Two things I would get out of it beyond durability:

- **Overlap solves itself** if I partition by job id. Every occurrence of a job
  lands on the same partition and is consumed in order, so the same job cannot
  run twice concurrently.
- **Workers scale independently of the scheduler.** Consumer lag becomes a metric
  I can alarm on and scale against, instead of `CallerRunsPolicy` quietly
  stalling my polling thread.

The honest caveat is that this converts the problem rather than eliminating it.
Kafka consumers are at-least-once by default, so a consumer that completes a job
and dies before committing its offset will replay it — a skipped run becomes a
duplicated one. That is the better failure for HTTP jobs, which can carry an
idempotency key (`jobId:scheduledFireTime`) for the receiver to dedupe on, and
the worse failure for email, where the recipient simply gets it twice. Exactly-
once still is not reachable by the scheduler alone; the most I can do is choose
which side to fail on per job type.

**Look-ahead polling for accurate fire times.** Instead of only claiming what is
already due, claim a window ahead — say the next 30 seconds — and hold each job
on an in-memory timer so it fires at its exact `next_run_at` rather than whenever
the next poll happens to land. That decouples precision from poll interval. The
cost is a wider crash window, since prefetched jobs are already claimed.

**Batch the schedule updates.** Right now each claimed job is updated
individually, so a batch of 100 due jobs is 100 statements against the same rows
the next poll will read. Rewriting the advance as a single batched statement
would cut that to one round trip and stop the database being hammered every poll
by every instance.

**Redis as the hot index and the coordination layer.** With Kafka in front of the
workers, the remaining bottleneck is the polling side: every instance runs the
same `FOR UPDATE SKIP LOCKED` query against the same rows on every tick, which is
row-lock contention that grows with the number of schedulers. Redis is how I
would take that pressure off Postgres.

The shape I have in mind:

1. **Postgres stays the source of truth.** Job definitions and run history live
   there; nothing below is allowed to be the only copy of anything.
2. **A Redis sorted set is the due-time index.** Key per shard, member is the job
   id, score is `next_run_at` as an epoch. Created and updated whenever a job is
   written through the API.
3. **Polling becomes a range read**, not a table scan:
   `ZRANGEBYSCORE due_jobs -inf (now + lookahead)`. That is O(log n) against
   memory, no locks, and it is the same look-ahead window described above — the
   scheduler picks up work slightly before it is due so it can fire at the exact
   second rather than whenever the poll lands.
4. **A `SETNX` on `jobId:scheduledFireTime` decides who publishes.** Whichever
   instance claims the key wins that occurrence and produces to Kafka; the others
   move on. Because the key is derived from the scheduled fire time rather than
   the wall clock, it is stable across instances and retries, so the same
   occurrence cannot be published twice even if two pollers race.
5. **Kafka partitioned by job id**, workers consume and execute.
6. **Postgres is updated in batch afterwards** — one statement advancing
   `next_run_at` for the whole claimed set, instead of a write per job. This is
   the point at which the earlier batching idea matters most, because it is what
   stops every scheduler instance hammering the database on every tick.

The job definitions themselves would also be cached in Redis, so a poll that
finds fifty due ids does not turn into fifty row reads.

What I would have to be careful about: Redis is not durable in the way Postgres
is, so the sorted set has to be treated as a rebuildable projection — on a cold
start or a flush, repopulate it from `cron_jobs` rather than assuming it is
correct. The `SETNX` keys need a TTL long enough to cover a slow publish but
short enough that they do not accumulate. And this only removes contention from
the *read* path; the claim still has to be idempotent, because Redis giving one
instance the key is a coordination hint, not a transactional guarantee.
