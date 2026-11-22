package com.votivo.result.repository;

import com.votivo.result.api.PollResult;
import com.votivo.result.domain.PollSnapshot;
import com.votivo.result.domain.PollState;
import com.votivo.result.domain.VoteAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage of the counters. The write methods expect to run inside the transaction of {@code ResultService}. */
public interface ResultRepository {
    /** Records the event id; false means it was applied before, so the caller must skip the event. */
    boolean markProcessed(UUID eventId);

    /** Stores the structure of a started poll. Votes counted so far and the state of the poll stay. */
    void saveStructure(PollSnapshot poll);

    /** Moves the poll to the state, also when its start event has not arrived yet. */
    void setState(UUID pollId, PollState state);

    /** Adds {@code delta} (+1 cast, -1 revoked) to the ballots of the poll and to every chosen option. */
    void addVote(UUID pollId, List<VoteAnswer> answers, int delta);

    Optional<PollResult> find(UUID pollId);
}
