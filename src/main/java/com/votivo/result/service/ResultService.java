package com.votivo.result.service;

import com.votivo.result.api.PollResult;
import com.votivo.result.api.PollResult.OptionResult;
import com.votivo.result.api.PollResult.QuestionResult;
import com.votivo.result.domain.VoteSubmission;
import com.votivo.result.domain.VoteSubmission.Answer;
import com.votivo.result.repository.VoteRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ResultService {
    private final VoteRepository voteRepository;

    public ResultService(VoteRepository voteRepository) {
        this.voteRepository = voteRepository;
    }

    public PollResult getResults(UUID pollId) {
        List<VoteSubmission> votes = voteRepository.findByPollId(pollId);
        Map<UUID, List<Answer>> answersByQuestion = votes.stream()
                .flatMap(vote -> vote.answers().stream())
                .collect(Collectors.groupingBy(Answer::questionId));
        List<QuestionResult> questions = answersByQuestion.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> summarizeQuestion(entry.getKey(), entry.getValue()))
                .toList();
        return new PollResult(pollId, votes.size(), questions);
    }

    private QuestionResult summarizeQuestion(UUID questionId, List<Answer> answers) {
        Map<UUID, Long> counts = answers.stream()
                .flatMap(answer -> answer.optionIds().stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<OptionResult> options = counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new OptionResult(
                        entry.getKey(), entry.getValue(),
                        Math.round(entry.getValue() * 1000.0 / answers.size()) / 10.0))
                .toList();
        return new QuestionResult(questionId, answers.size(), options);
    }
}
