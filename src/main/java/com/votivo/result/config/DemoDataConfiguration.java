package com.votivo.result.config;

import com.votivo.result.domain.VoteSubmission;
import com.votivo.result.domain.VoteSubmission.Answer;
import com.votivo.result.repository.VoteRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoDataConfiguration {
    @Bean
    CommandLineRunner seedDemoVotes(VoteRepository repository) {
        return args -> {
            UUID pollId = UUID.fromString("11111111-1111-4111-8111-111111111111");
            UUID questionId = UUID.fromString("22222222-2222-4222-8222-222222222222");
            UUID morning = UUID.fromString("33333333-3333-4333-8333-333333333333");
            UUID afternoon = UUID.fromString("44444444-4444-4444-8444-444444444444");
            repository.save(new VoteSubmission(
                    UUID.fromString("55555555-5555-4555-8555-555555555555"), pollId,
                    List.of(new Answer(questionId, Set.of(morning)))));
            repository.save(new VoteSubmission(
                    UUID.fromString("66666666-6666-4666-8666-666666666666"), pollId,
                    List.of(new Answer(questionId, Set.of(morning, afternoon)))));
            repository.save(new VoteSubmission(
                    UUID.fromString("77777777-7777-4777-8777-777777777777"), pollId,
                    List.of(new Answer(questionId, Set.of(afternoon)))));
        };
    }
}
