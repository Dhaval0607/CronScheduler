# CronScheduler — Design Write-up

## Approach

I started from the data model rather than the scheduling logic, on the view that
a cron scheduler is a state machine over a table: everything the scheduler does
is decided by what the row says. Getting the columns right first meant the
polling loop had almost nothing left to decide.

So the build went in three phases:

1. **CRUD API and the job model** — a `cron_jobs` table with validation, so jobs
   could be created and inspected before anything ran them.
2. **The scheduler** — polling for due jobs, claiming them safely, and executing
   them by type.
3. **Run history** — a `job_runs` table recording every execution.

---

## Phase 1 — CRUD and the data model

The first cut was `CronJob` plus a REST resource over it: create, list, fetch,
update, delete. Two rules were enforced from the start, because both are cheap
at write time and expensive at fire time:

- **The cron expression is validated on write** via Spring's `CronExpression`.
  An unparseable expression is a `400`, not a job that silently never fires.
- **Names are unique, case-insensitively**, so a job has a stable human handle.

### `cron_jobs`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `bigint` identity | no | PK |
| `name` | `varchar(255)` | no | unique |
| `description` | `varchar(255)` | yes | |
| `cron_expression` | `varchar(255)` | no | Spring 6-field syntax |
| `job_type` | `varchar(255)` | no | `EMAIL` \| `HTTP`, check constraint |
| `payload` | `jsonb` | yes | type-specific config |
| `enabled` | `boolean` | no | on/off switch |
| `next_run_at` | `timestamptz` | yes | the scheduler's work queue |
| `last_run_at` | `timestamptz` | yes | stamped at claim time |
| `created_at` | `timestamptz` | no | |
| `updated_at` | `timestamptz` | no | |

Two columns carry most of the design:

**`next_run_at` is the queue.** Rather than the scheduler evaluating every cron
expression on every tick, each job stores its own next fire time and the poll
becomes an indexed range scan. Cron parsing happens once per execution instead
of once per job per poll.

**`payload` is `jsonb`, not per-type columns.** An email job needs
`to`/`subject`/`body`; an HTTP job needs `url`/`method`/`headers`/`body`. They
share nothing, so columns would leave every row half-null and force a schema
migration for each new job type. The tradeoff is that the database can't validate
the shape — so the application does, at create/update time, by handing the
payload to the executor that will eventually run it.

`enabled` is enforced twice: disabling a job nulls `next_run_at`, *and* the due
query filters on `enabled = true`. Either alone would suffice; both together
mean a disabled job cannot be picked up through any path.

---

## Phase 2 — The scheduler

A poll loop runs every 5 seconds and asks one question: which jobs have
`next_run_at <= now`?

**Claiming is the hard part, not polling.** With more than one instance running,
two schedulers can see the same due row and fire it twice. The claim query uses:

```sql
SELECT * FROM cron_jobs
WHERE enabled = true AND next_run_at IS NOT NULL AND next_run_at <= :now
ORDER BY next_run_at
LIMIT :limit
FOR UPDATE SKIP LOCKED
```

`SKIP LOCKED` makes concurrent instances take *disjoint* slices instead of
blocking on each other. Postgres arbitrates, so no distributed lock, no leader
election, no extra infrastructure. The claim transaction is deliberately short —
it advances `next_run_at`, stamps `last_run_at`, and commits, releasing the row
locks *before* any job actually runs.

**Missed runs are collapsed, not replayed.** A job that falls five intervals
behind fires once and resumes. Replaying every missed occurrence is almost never
what a cron user wants after an outage.

**Execution is routed by type.** A `JobExecutorRegistry` receives every
`JobExecutor` bean as a `List` and indexes them by `JobType`. Adding a job type
means adding one bean — the registry finds it and the scheduler is untouched.
Two executors claiming the same type is a startup failure, not a silent
last-one-wins.

**Polling is single-threaded; execution is not.** `fixedDelay` is non-reentrant,
so claims can never overlap — that part must stay serial. But jobs are dispatched
to a bounded thread pool, so one slow HTTP call delays neither the rest of its
batch nor the next poll. When the queue fills, `CallerRunsPolicy` pushes work
back onto the polling thread: deliberate backpressure rather than an unbounded
queue that grows until the heap gives out.

---

## Phase 3 — Run history

Every execution writes a row: status, duration, timestamps, and the failure
reason when there is one.

### `job_runs`

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | `bigint` identity | no | PK |
| `job_id` | `bigint` | no | **no FK** — see below |
| `job_name` | `varchar(255)` | no | denormalised |
| `job_type` | `varchar(255)` | no | denormalised |
| `status` | `varchar(255)` | no | `SUCCESS` \| `FAILED` |
| `started_at` | `timestamptz` | no | indexed |
| `finished_at` | `timestamptz` | no | |
| `duration_ms` | `bigint` | no | |
| `error_message` | `varchar(2000)` | yes | truncated |

**No foreign key to `cron_jobs`, and the job's name and type are copied in.**
History is an audit trail, so it has to outlive what it describes. A real FK
forces a choice between blocking deletion of any job that has ever run, or
cascading its history away — both wrong. Denormalising name and type keeps the
history readable after the job is gone. The cost is rows that reference jobs
which no longer exist, which is the correct direction to fail.

`error_message` is capped at 2000 characters because stack traces and HTML error
pages are unbounded and columns are not.

---

## Tradeoffs and known limitations

**At-most-once delivery.** `next_run_at` advances at claim time, so a crash
mid-execution skips that run rather than repeating it. Advancing at completion
would give at-least-once and risk sending an email twice. Neither is "correct" —
exactly-once isn't reachable by the scheduler alone, since a crash between
performing a side effect and recording it is indistinguishable from a crash
before it. The real fix is receiver-side dedup on a deterministic key
(`jobId:scheduledFireTime`), which suits HTTP jobs and doesn't suit email.

**No overlap guard.** A job whose execution outlasts its interval can be claimed
again while the previous run is still going. Fixing it needs a lease column plus
a reaper for leases orphaned by a crashed instance.

**`last_run_at` is stamped at claim time**, not completion, so a queued job
briefly looks like it already ran. `job_runs` is the accurate record.

**Poll interval bounds precision.** A job is up to 5s late, never early. For
minute-granularity cron that's fine; tighter precision wants look-ahead prefetch
(claim a window ahead, fire on an in-memory timer), which trades precision for a
wider crash window.

**Operational gaps**: `job_runs` is never pruned; `EmailJobExecutor` validates
and logs rather than sending, pending SMTP credentials; and schema is managed by
`ddl-auto=update`, which cannot add a `NOT NULL` column to a populated table —
Flyway or Liquibase is the right answer before this runs anywhere shared.

---

## Testing

49 tests. All but one are plain unit tests — no Spring context, no database,
sub-second. Coverage is aimed at the decisions rather than the plumbing:
boundary behaviour in cron resolution, missed-interval collapsing, registry
resolution and duplicate detection, payload validation, error truncation, limit
clamping, and the failure-isolation guarantees (a failing job must not stop the
batch; a failed claim must not kill the scheduled task; a failed history write
must not fail the job).

One test earned its keep immediately: asserting that an unsupported HTTP verb is
rejected revealed that Spring's `HttpMethod` is not an enum — `valueOf("TELEPORT")`
returns an instance rather than throwing, so the validation never fired and any
garbage verb would have passed create and failed at fire time. Fixed with an
explicit allowlist.
