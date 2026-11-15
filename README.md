# Votivo Result Service

Aggregates votes and provides poll results and statistics for Votivo.

## Requirements

- JDK 25; set `JAVA_HOME` to its installation directory.
- PostgreSQL 18; persistence tests use PostgreSQL 18.3.
- The committed Gradle Wrapper downloads Gradle automatically.
- Docker is required only for container-based integration tests, or when you choose to host your development database in Docker.

## Prepare the database

Use a dedicated database and role for Result Service. For example, run these commands in `psql` while connected as a database administrator:

```sql
CREATE ROLE votivo_results LOGIN;
\password votivo_results
CREATE DATABASE votivo_results OWNER votivo_results;
```

The `\password` command prompts for a password. Set the same password in the service environment. If your team already provisioned a database and account, use those instead.

Flyway creates the application tables on service startup. No manual table creation is needed.

This component does not include a Docker Compose file. Shared deployment and database containers belong in the Votivo integration repository. The service can connect to either a local database or the database provisioned by the future shared Compose.

## Configure and run

`.env.example` documents the environment variables. Spring Boot does not automatically read `.env` files: export the variables, configure them in your IDE, or supply them through the future shared Compose.

Windows PowerShell:

```powershell
$env:RESULTS_DB_URL = 'jdbc:postgresql://localhost:5432/votivo_results'
$env:RESULTS_DB_USER = 'votivo_results'
$env:RESULTS_DB_PASSWORD = 'your-local-password'
.\gradlew.bat bootRun
```

Linux / macOS:

```bash
export RESULTS_DB_URL='jdbc:postgresql://localhost:5432/votivo_results'
export RESULTS_DB_USER='votivo_results'
read -rs -p 'Database password: ' RESULTS_DB_PASSWORD
export RESULTS_DB_PASSWORD
sh ./gradlew bootRun
```

The HTTP port defaults to 8083 and can be overridden with `SERVER_PORT`. The database URL and username default to the values shown above; the password has no committed default.

When the application runs inside Docker, use the database service's network hostname in `RESULTS_DB_URL` rather than `localhost`. The database must be reachable before the application starts.

## Demo data

After configuring the database, optionally populate the frontend sample poll:

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=demo'
```

The demo profile adds three synthetic submissions with fixed IDs. Repeated starts do not duplicate them. These rows persist in PostgreSQL after restarting or disabling the demo profile. Use a development database for this profile.

## API

- `GET /actuator/health` - application and database health.
- `GET /api/v1/polls/{pollId}/results` - aggregate results for a UUID poll ID.

Demo request:

```text
GET http://localhost:8083/api/v1/polls/11111111-1111-4111-8111-111111111111/results
```

A result contains `pollId`, `totalVotes`, and `questions`. Each question includes `questionId`, `answeredVotes`, and `options`; each option includes `optionId`, `votes`, and `percentage`.

Percentages use the number of submissions answering the question as the denominator, rounded to one decimal place. Multiple-choice percentages can sum to more than 100%. Questions and options without received answers are absent until integration supplies the poll definition.

An unknown poll UUID returns an empty result because Poll Service lookup is not implemented. A malformed UUID returns HTTP 400.

## Persistence and migrations

Flyway runs versioned SQL from `src/main/resources/db/migration/` and records applied migrations in `flyway_schema_history`. Migration `V1__create_result_projection.sql` creates:

- `result_vote` - processed submission IDs and poll IDs, with an index on poll ID.
- `result_answer` - questions answered in each submission.
- `result_selection` - selected answer options.

These tables hold a Result Service projection, not Vote Service's source-of-truth database. There are no cross-service foreign keys. One transaction writes a submission and all its selections; a repeated vote ID is ignored, including under concurrent delivery.

Keep applied migrations unchanged. Add subsequent schema changes as `V2__...`, `V3__...`, and so on. Flyway clean is disabled.

## Structure

- `domain/` - immutable vote and answer models.
- `repository/` - persistence abstraction and PostgreSQL JDBC adapter.
- `service/` - result aggregation.
- `api/` - REST controller and response records.
- `config/` - optional demo data.
- `src/main/resources/db/migration/` - Flyway migrations.
- `src/test/` - isolated unit/HTTP tests and PostgreSQL integration tests.
- `docs/` - development and integration notes.

## Checks

Fast aggregation and HTTP contract tests, without Docker or an external database:

```powershell
.\gradlew.bat test
```

Migration, persistence, duplicate-delivery, and rollback tests against a disposable PostgreSQL container; Docker must be running:

```powershell
.\gradlew.bat integrationTest
```

Full verification and executable JAR:

```powershell
.\gradlew.bat check bootJar
```

Integration tests do not use your configured development database and are not silently skipped when Docker is unavailable. Testcontainers owns and removes the temporary database container.

## Responsibility and scope

Ruslan Kolisnychenko owns Result Service and the Angular frontend.

There is no public vote-ingestion endpoint. Keycloak authorization, RabbitMQ consumption, poll metadata, vote changes/cancellation, and production deployment remain integration work. A repeated vote ID does not update an existing submission. The API remains a development scaffold without enforcement of poll visibility.

[Frontend](https://github.com/koliruslik/Votivo-frontend/tree/main) | [Integration repository](https://github.com/koliruslik/Votivo/tree/main)

## Working as a submodule

Work directly inside `Votivo/backend/services/result-service`. Use this repository's own feature branch and commit here. Updating the component revision in the parent repository is a separate change.
