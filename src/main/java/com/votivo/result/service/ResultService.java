package com.votivo.result.service;

import com.votivo.result.api.PollResult;
import com.votivo.result.repository.VoteRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResultService {
    private final VoteRepository voteRepository;

    public ResultService(VoteRepository voteRepository) {
        this.voteRepository = voteRepository;
    }

    public PollResult getResults(UUID pollId) {
        return new PollResult(pollId, voteRepository.findByPollId(pollId).size());
    }
}
