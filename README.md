# Votivo Result Service

Spring Boot foundation for the service responsible for poll results.

## Run

Use JDK 25 and set JAVA_HOME to its installation directory. The committed Gradle Wrapper downloads Gradle automatically.

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

Linux / macOS:

```bash
sh ./gradlew bootRun
```

The HTTP port defaults to 8083 and can be overridden with SERVER_PORT. No database or Docker is required.

## Laboratory 4 scope

This is a runnable code skeleton with a minimal result-counting operation:

- VoteSubmission and PollResult models.
- VoteRepository abstraction and its in-memory implementation.
- ResultService, which counts stored submissions for a poll.
- ResultController, which exposes that count over HTTP.

The repository starts empty. There is no vote-ingestion endpoint, database, message consumer, demo seed, or breakdown by question and option.

## API

```text
GET http://localhost:8083/api/v1/polls/11111111-1111-4111-8111-111111111111/results
```

Example response:

```json
{
  "pollId": "11111111-1111-4111-8111-111111111111",
  "totalVotes": 0
}
```

Unknown poll UUIDs return an empty count because Poll Service lookup is not implemented. Malformed UUIDs return HTTP 400.

## Structure

- `domain/` - initial vote model.
- `repository/` - storage interface and in-memory implementation.
- `service/` - minimal result logic.
- `api/` - controller and response model.
- `src/test/` - startup and HTTP checks.
- `docs/` - development notes.

## Checks and build

```powershell
.\gradlew.bat test bootJar
```

## Team responsibility

Ruslan Kolisnychenko owns Result Service and the Angular frontend. The rest of the team owns User Service, Poll Service, and Vote Service.

PostgreSQL, schema migrations, RabbitMQ, access control, poll metadata, and full result aggregation remain later work.

[Frontend](https://github.com/koliruslik/Votivo-frontend/tree/main) | [Integration repository](https://github.com/koliruslik/Votivo/tree/main)

## Working locally

Work directly inside `Votivo/backend/services/result-service` on this repository's feature branch. Commit changes here and review them through a PR. Updating the parent repository's component reference is a separate step.
