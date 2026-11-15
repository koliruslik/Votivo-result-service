# Votivo Result Service

Aggregates votes and provides poll results and statistics for Votivo.

## Requirements and quick start

- JDK 25; set `JAVA_HOME` to its installation directory.
- The committed Gradle Wrapper downloads Gradle automatically; no global Gradle installation is needed.

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

Linux / macOS:

```bash
sh ./gradlew bootRun
```

The service listens on `http://localhost:8083`. Override the port with `SERVER_PORT`.

To populate three demonstration votes for the frontend sample poll, use the opt-in demo profile:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=demo'
```

The default profile starts with no votes. All data is held in memory and is lost on restart.

## API

- `GET /actuator/health` - application health.
- `GET /api/v1/polls/{pollId}/results` - aggregate results for a UUID poll ID.

Demo request:

```text
GET http://localhost:8083/api/v1/polls/11111111-1111-4111-8111-111111111111/results
```

A result contains `pollId`, `totalVotes`, and `questions`. Each question includes `questionId`, `answeredVotes`, and `options`; each option includes `optionId`, `votes`, and `percentage`.

Percentages use the number of submissions answering the question as the denominator, rounded to one decimal place. For multiple-choice questions, percentages can sum to more than 100%. Questions and options without received answers are absent until integration supplies the poll definition.

An unknown poll UUID returns an empty result, because this scaffold has no Poll Service lookup. A malformed UUID returns HTTP 400.

## Structure

- `domain/` - immutable vote and answer models.
- `repository/` - persistence abstraction and in-memory adapter.
- `service/` - result aggregation.
- `api/` - REST controller and response records.
- `config/` - optional demo data.
- `src/test/` - aggregation, duplicate-delivery, and HTTP contract tests.
- `docs/` - integration notes.

## Checks

```powershell
.\gradlew.bat test
.\gradlew.bat bootJar
```

## Responsibility and scope

Ruslan Kolisnychenko owns Result Service and the Angular frontend. This repository supplies the laboratory 4 code foundation: runnable application, layered modules, minimal aggregation, and tests.

There is no public vote-ingestion endpoint. Keycloak authorization, RabbitMQ consumption, PostgreSQL, poll metadata, vote changes/cancellation, and production deployment remain integration work. Duplicate IDs are ignored by the in-memory repository; full message-delivery handling is not implemented. The API is a local development scaffold and does not enforce poll visibility.

[Frontend](https://github.com/koliruslik/Votivo-frontend/tree/main) | [Integration repository](https://github.com/koliruslik/Votivo/tree/main)

## Working as a submodule

Work directly inside `Votivo/backend/services/result-service`. Use this repository's own feature branch and commit here. Updating the component revision in the parent repository is a separate change.
