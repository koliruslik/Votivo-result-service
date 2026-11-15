# Development notes

ResultController delegates to ResultService. ResultService depends on VoteRepository, with InMemoryVoteRepository as the initial adapter.

VoteSubmission contains a vote ID and poll ID. The current result operation counts submissions for the requested poll. The repository starts empty; there is no HTTP endpoint for adding votes.

This is the laboratory 4 foundation: a runnable application with basic models, layers, and minimal behavior. PostgreSQL, Flyway, RabbitMQ, authentication, detailed aggregation, and cross-service integration are outside this version.

Work on a feature branch inside this submodule. Keep parent-repository pointer changes separate.
