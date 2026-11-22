package com.votivo.result.service;

import com.votivo.result.api.PollResult;
import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import com.votivo.result.repository.ResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Applies the events and answers the result queries. Every event is applied in one transaction together with
 * its event id: delivery is at least once, so an id that was applied before is skipped, and a failure rolls back
 * both the counters and the id, so the redelivered message is applied once.
 */
@Service
public class ResultService {
    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    private final ResultRepository repository;

    public ResultService(ResultRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void pollStarted(UUID eventId, PollSnapshot poll) {
        if (!repository.markProcessed(eventId)) return;
        repository.saveStructure(poll);
        log.info("Poll {} started: {} questions", poll.pollId(), poll.questions().size());
    }

    @Transactional
    public void pollEnded(UUID eventId, UUID pollId, PollState state) {
        if (!repository.markProcessed(eventId)) return;
        repository.setState(pollId, state);
        log.info("Poll {} ended: {}", pollId, state);
    }

    @Transactional
    public void voteCast(UUID eventId, UUID pollId, List<VoteAnswer> answers) {
        if (!repository.markProcessed(eventId)) return;
        repository.addVote(pollId, answers, 1);
    }

    @Transactional
    public void voteRevoked(UUID eventId, UUID pollId, List<VoteAnswer> answers) {
        if (!repository.markProcessed(eventId)) return;
        repository.addVote(pollId, answers, -1);
    }

    @Transactional(readOnly = true)
    public PollResult getResults(UUID pollId) {
        return repository.find(pollId).orElseGet(() -> PollResult.unknown(pollId));
    }
}
