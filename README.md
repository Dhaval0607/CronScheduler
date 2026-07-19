# CronScheduler

A database-backed cron scheduler. Jobs are stored in Postgres, polled when due,
and executed by a type-specific executor. Every execution is recorded with its
status, duration, and failure reason.

Built with Spring Boot 4.1, Java 21, Postgres 16.

---

## Prerequisites

| | |
|---|---|
| JDK 21+ | `java -version` |
| Docker | for the Postgres container |
| Maven | not needed — use the bundled `./mvnw` |

## Install and run

```bash
# 1. start Postgres (localhost:5434)
docker compose up -d

# 2. run the app (localhost:8081)
./mvnw spring-boot:run
```

The schema is created automatically on first start (`spring.jpa.hibernate.ddl-auto=update`).
Once you see `Started CronSchedulerApplication`, the API is live:

```bash
curl localhost:8081/api/v1/jobs
```

To run on a different port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

## Test

```bash
./mvnw test
```

49 tests. All but one are plain unit tests with no external dependencies and run
in under a second.

`CronSchedulerApplicationTests.contextLoads` is the exception — it starts the
full Spring context and **needs Postgres running**. If `docker compose up -d`
hasn't been run, that one test fails while the other 48 still pass.

---

## API

Base path `/api/v1`. All request bodies are `application/json`.

### Jobs

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/jobs` | Create a job |
| `GET` | `/jobs` | List all jobs |
| `GET` | `/jobs/{id}` | Fetch one job |
| `PUT` | `/jobs/{id}` | Replace a job |
| `DELETE` | `/jobs/{id}` | Delete a job (`204`) |
| `GET` | `/jobs/{id}/runs?limit=` | Run history for one job |

### Runs

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/runs?limit=` | Recent runs across all jobs |

`limit` defaults to 50 and is capped at 500.

### Create an EMAIL job

```json
{
  "name": "nightly-report",
  "description": "emails the daily summary",
  "cronExpression": "0 0 9 * * *",
  "jobType": "EMAIL",
  "payload": {
    "to": "someone@example.com",
    "subject": "Daily summary",
    "body": "Your report is ready."
  },
  "enabled": true
}
```

### Create an HTTP job

```json
{
  "name": "ping-webhook",
  "cronExpression": "0 */5 * * * *",
  "jobType": "HTTP",
  "payload": {
    "url": "https://example.com/hook",
    "method": "POST",
    "headers": { "Authorization": "Bearer token" },
    "body": { "event": "heartbeat" }
  }
}
```

`method` defaults to `POST`. `headers` and `body` are optional.

### Cron format

Spring's **six-field** syntax — seconds come first:

```
second minute hour day-of-month month day-of-week
```

```
*/10 * * * * *     every 10 seconds
0 * * * * *        every minute
0 0 9 * * *        09:00 daily
0 0 9 * * MON-FRI  09:00 on weekdays
```

A standard five-field crontab string (`0 9 * * *`) is rejected with `400`.
Expressions resolve in the server's local timezone.

---

## How it works

```
 SchedulerService ──poll every 5s──> JobClaimer ──SELECT … FOR UPDATE SKIP LOCKED──> Postgres
        │                                 └── advances next_run_at, stamps last_run_at
        │
        └──dispatch──> execution pool ──> JobExecutorRegistry ──> EmailJobExecutor
                                                               └─> HttpJobExecutor
                                                                        │
                                                              JobRunRecorder ──> job_runs
```

**Polling** is single-threaded. `fixedDelay` is non-reentrant, so claims can
never overlap.

**Claiming** uses `FOR UPDATE SKIP LOCKED`, so multiple instances take disjoint
slices of the due rows rather than double-firing a job. The transaction is kept
short — locks are released before any job runs.

**Execution** happens on a bounded thread pool, so one slow job delays neither
the rest of its batch nor the next poll. When the queue fills, `CallerRunsPolicy`
pushes work back onto the polling thread as backpressure.

**Routing** is by `jobType`. Adding a job type means adding one `JobExecutor`
bean — the registry discovers it, and `SchedulerService` needs no change. Two
executors claiming the same type fails at startup rather than silently shadowing.

**Validation** of payloads happens at create/update time, so a missing recipient
or a relative URL is rejected by the API rather than discovered at 3am.

### Packages

```
com.cron.controller   REST endpoints
com.cron.service      job CRUD, run-history queries
com.cron.scheduler    polling, claiming, thread pool, history recording
com.cron.executor     JobExecutor interface, registry, EMAIL + HTTP executors
com.cron.model        JPA entities
com.cron.repository   Spring Data repositories
com.cron.dto          request/response records
com.cron.exception    domain exceptions + global handler
```

---

## Configuration

All values in `src/main/resources/application.properties`.

| Property | Default | Purpose |
|---|---|---|
| `scheduler.poll-interval-ms` | `5000` | Delay between polls |
| `scheduler.initial-delay-ms` | `5000` | Delay before the first poll |
| `scheduler.batch-size` | `100` | Max jobs claimed per poll |
| `scheduler.execution.core-pool-size` | `5` | Threads kept alive |
| `scheduler.execution.max-pool-size` | `10` | Ceiling, reached only when the queue is full |
| `scheduler.execution.queue-capacity` | `50` | Queued jobs before backpressure |
| `scheduler.execution.shutdown-await-seconds` | `30` | Drain time on shutdown |
| `scheduler.http.connect-timeout-ms` | `5000` | HTTP executor connect timeout |
| `scheduler.http.read-timeout-ms` | `10000` | HTTP executor read timeout |

Thread pools fill core threads → queue → extra threads → reject. With the
defaults, `max-pool-size` is only reached once 55 jobs are pending; to get burst
capacity sooner, shrink `queue-capacity` rather than raising `max-pool-size`.

---

## Known limitations

Deliberate scope cuts, not oversights:

- **`EmailJobExecutor` is a stub.** It validates the payload and logs what it
  would send. Real delivery is a `JavaMailSender` call away, pending SMTP
  credentials.
- **At-most-once delivery.** `next_run_at` advances at claim time, so a crash
  mid-execution skips that run rather than repeating it. Advancing at completion
  would give at-least-once and risk duplicate sends instead.
- **No overlap guard.** A job whose execution outlasts its interval can be
  claimed again while the previous run is still going.
- **`last_run_at` is stamped at claim time**, not completion, so a queued job
  briefly looks like it already ran. `job_runs` is the accurate record.
- **`job_runs` is never pruned.** A job on `*/10 * * * * *` writes ~8,600 rows
  per day.
- **Schema is managed by `ddl-auto=update`**, which cannot add a `NOT NULL`
  column to a table with existing rows. A real migration tool (Flyway/Liquibase)
  is the right answer before this runs anywhere shared.
