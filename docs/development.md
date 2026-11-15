# Development notes

The controller delegates to ResultService, which depends on VoteRepository rather than a storage implementation. InMemoryVoteRepository is the initial adapter.

VoteSubmission is a complete submission for one poll and can contain several answers; one answer can select several distinct options. A submission cannot contain two answers for the same question. User identities are absent from result responses.

The demo profile supplies synthetic votes only. Later work must agree event schemas and poll metadata with Vote Service and Poll Service, then replace in-memory storage and add access control. API and event contracts are provisional until the team agrees them.

Work on a feature branch in this submodule. Commit and push in this repository, then use a PR. Do not stage the parent Votivo repository's changed submodule pointer as part of component work.
