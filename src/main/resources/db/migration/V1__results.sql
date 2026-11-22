-- Counters of a poll, fed by the events of Poll Service and Vote Service. A vote event can overtake poll.started
-- (the two travel in different queues), so every row can be created by either side and the other fills in the rest.

-- Event ids already applied: a redelivered message changes nothing.
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE poll_results (
    poll_id UUID PRIMARY KEY,
    title VARCHAR(200),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED', 'BLOCKED')),
    total_votes BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE question_results (
    question_id UUID PRIMARY KEY,
    poll_id UUID NOT NULL REFERENCES poll_results(poll_id) ON DELETE CASCADE,
    text VARCHAR(2000),
    multiple BOOLEAN NOT NULL DEFAULT FALSE,
    position INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE option_results (
    option_id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES question_results(question_id) ON DELETE CASCADE,
    text VARCHAR(1000),
    position INTEGER NOT NULL DEFAULT 0,
    votes BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_question_results_poll ON question_results(poll_id, position);
CREATE INDEX idx_option_results_question ON option_results(question_id, position);
