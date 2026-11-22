# Votivo Result Service

Spring Boot service that counts the votes of a poll and serves the results. It owns no poll or vote: it learns about both from RabbitMQ events and keeps its own counters in PostgreSQL (`result_db`).

## Run

Use JDK 25. The committed Gradle Wrapper downloads Gradle automatically.

```powershell
.\gradlew.bat bootRun
```

Defaults point at a local PostgreSQL (`result_db`, user and password `result`) and RabbitMQ (`guest`/`guest`); the whole system, including both, starts from the integration repository with `docker compose up -d --build --wait` in `backend/`.

| Variable | Default | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8083` | HTTP port |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/result_db` / `result` / `result` | PostgreSQL |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | broker address |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | `guest` / `guest` / `/` | broker credentials |
| `RABBITMQ_LISTENERS_ENABLED` | `true` | switch the consumers off |

## Events

Result Service consumes two queues; it declares them, their bindings and their dead-letter queues itself, so the start order of the services does not matter.

| Queue | Exchange (topic) | Routing key | Effect |
| --- | --- | --- | --- |
| `result-service.poll-events` | `poll.events.exchange` | `poll.started` | stores the title, questions and options: every option is listed with `0` votes |
| | | `poll.ended` | marks the result final: `status` becomes `CLOSED` or `BLOCKED` |
| `result-service.vote-events` | `vote.events.exchange` | `vote.cast` | `+1` ballot and `+1` for every chosen option |
| | | `vote.revoked` | `-1` ballot and `-1` for every option of the old answers (a changed vote is a revoke and a cast) |

Delivery is at least once. The AMQP `message_id` is the event id: it is stored in `processed_events` in the same transaction as the counters, so a redelivered message changes nothing and a failed one is applied exactly once when it comes back. The two queues are independent, so a vote can be processed before `poll.started`; the counters are then created by the vote and the structure fills in later without resetting them.

A message that no retry can fix (no `message_id`, a body that does not decode, an unknown routing key or reason) is rejected without requeue. A failure such as a database outage is retried in the consumer (5 attempts, 1s doubling). In both cases the queue dead-letters the message to `votivo.dlx`, which keeps it in `<queue>.dlq` for inspection. Event bodies are described in [`docs/events.md`](../../../docs/events.md) of the integration repository, [Poll Service](https://github.com/Konnzy/Votivo-poll-service) and [Vote Service](https://github.com/ReilEgor/Votivo-vote-service).

## API

```text
GET /api/v1/polls/{pollId}/results
```

```json
{
  "pollId": "11111111-1111-4111-8111-111111111111",
  "title": "Lunch",
  "status": "ACTIVE",
  "totalVotes": 3,
  "questions": [
    {
      "questionId": "22222222-2222-4222-8222-222222222222",
      "text": "Where?",
      "multiple": false,
      "options": [
        { "optionId": "33333333-3333-4333-8333-333333333333", "text": "Pizza", "votes": 2 },
        { "optionId": "44444444-4444-4444-8444-444444444444", "text": "Sushi", "votes": 1 }
      ]
    }
  ]
}
```

`totalVotes` counts ballots; in a multiple-choice question the options add up to more. `status` is `ACTIVE`, `CLOSED` or `BLOCKED` (the result is final), or `UNKNOWN` when no event about the poll has arrived yet (`title` is `null`, `questions` is empty). A malformed poll id returns `400`.

`GET /actuator/health/liveness` and `/actuator/health/readiness` (checks the database) are public.

## Structure

- `messaging/` - exchanges, queues, dead-letter topology and the event listener.
- `service/` - applies each event once in one transaction; answers result queries.
- `repository/` - `ResultRepository` and its PostgreSQL implementation (`JdbcClient`, `ON CONFLICT` upserts).
- `api/` - controller and response model.
- `src/main/resources/db/migration/` - Flyway schema.
- `src/test/` - unit tests with an in-memory repository, listener tests, API tests on H2.

## Checks and build

```powershell
.\gradlew.bat test bootJar
```

The repository tests need a PostgreSQL (the upserts are PostgreSQL-specific) and are excluded from `test`:

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:15432/result_db'   # TEST_DB_USERNAME / TEST_DB_PASSWORD default to result
.\gradlew.bat postgresTest
```

## Not done yet

Access control (Keycloak) and the poll metadata lookup from Poll Service are not implemented; the results are public.

[Frontend](https://github.com/koliruslik/Votivo-frontend/tree/main) | [Integration repository](https://github.com/koliruslik/Votivo/tree/main)

## Working locally

Work directly inside `Votivo/backend/services/result-service` on this repository's feature branch. Commit changes here and review them through a PR. Updating the parent repository's component reference is a separate step.
