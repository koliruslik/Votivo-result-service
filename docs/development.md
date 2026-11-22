# Development notes

`ResultEventListener` (messaging) turns the broker messages into calls of `ResultService`, which applies each event once together with its event id in one transaction. `ResultRepository` is the storage interface; `JdbcResultRepository` is its PostgreSQL implementation and the only place with SQL.

Both consumed queues are independent, so the repository must accept a vote for a poll whose `poll.started` has not been seen: counters are created by whichever event comes first, and `saveStructure` only fills in titles, texts and positions.

Unit tests use `InMemoryResultRepository` (a test double with the same rules). The SQL itself is checked by `JdbcResultRepositoryPostgresTest` against a real PostgreSQL (`postgresTest`).

Work on a feature branch inside this submodule. Keep parent-repository pointer changes separate.
