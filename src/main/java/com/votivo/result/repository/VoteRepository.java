package com.votivo.result.repository;

import com.votivo.result.domain.VoteSubmission;
import java.util.List;
import java.util.UUID;

public interface VoteRepository {
    List<VoteSubmission> findByPollId(UUID pollId);

    // Repeated delivery of the same vote ID must not add another vote.
    void save(VoteSubmission vote);
}
