package com.votivo.result.repository;

import com.votivo.result.domain.VoteSubmission;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryVoteRepository implements VoteRepository {
    private final ConcurrentMap<UUID, VoteSubmission> votes = new ConcurrentHashMap<>();

    @Override
    public List<VoteSubmission> findByPollId(UUID pollId) {
        return votes.values().stream()
                .filter(vote -> vote.pollId().equals(pollId))
                .toList();
    }

    @Override
    public void save(VoteSubmission vote) {
        votes.putIfAbsent(vote.voteId(), vote);
    }
}
