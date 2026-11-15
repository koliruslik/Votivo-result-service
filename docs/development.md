# Development notes

The controller delegates to ResultService, which depends on VoteRepository. JdbcVoteRepository stores a PostgreSQL projection; InMemoryVoteRepository exists only in test sources.

VoteSubmission is a complete submission for one poll and can contain several answers; one answer can select several distinct options. A submission cannot contain two answers for the same question. User identities are absent from result responses.

Saving a submission is transactional. PostgreSQL's unique vote ID and INSERT ... ON CONFLICT DO NOTHING prevent concurrent redelivery from adding another vote. The first successfully stored payload wins; vote edits and cancellation are not implemented. A failed child-row insert rolls back the submission and its answers.

Flyway migrations run before normal application startup. V1 creates projection tables and a poll lookup index; subsequent schema changes need a new numbered migration. The database and account must already exist, and the account must be able to create these objects.

The demo profile supplies synthetic votes with fixed IDs. They are persisted and remain after the demo profile is disabled. It must only be used with a development database.

Environment configuration uses RESULTS_DB_URL, RESULTS_DB_USER, and RESULTS_DB_PASSWORD. .env.example is documentation rather than an automatically imported configuration file. The future integration repository Compose must provide these variables, the database service, persistent storage, and database readiness checks. No component Compose or changes to the parent repository are included here.

The PostgreSQL tests use Testcontainers and run under the integrationTest task; check includes them. They use an isolated disposable database instead of any existing development database. Unit and MVC slice tests run under test without a database.

Later work must agree event schemas and poll metadata with Vote Service and Poll Service, then add message consumption and access control. API and event contracts remain provisional until the team agrees them.

Work on a feature branch in this submodule. Commit and push in this repository, then use a PR. Keep parent-repository pointer changes separate.
