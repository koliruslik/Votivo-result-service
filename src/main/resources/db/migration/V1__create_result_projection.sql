CREATE TABLE result_vote (
    vote_id UUID PRIMARY KEY,
    poll_id UUID NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_result_vote_poll_id ON result_vote (poll_id);

CREATE TABLE result_answer (
    vote_id UUID NOT NULL REFERENCES result_vote (vote_id) ON DELETE CASCADE,
    question_id UUID NOT NULL,
    PRIMARY KEY (vote_id, question_id)
);

CREATE TABLE result_selection (
    vote_id UUID NOT NULL,
    question_id UUID NOT NULL,
    option_id UUID NOT NULL,
    PRIMARY KEY (vote_id, question_id, option_id),
    FOREIGN KEY (vote_id, question_id)
        REFERENCES result_answer (vote_id, question_id) ON DELETE CASCADE
);
