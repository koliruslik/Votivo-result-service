package com.votivo.result;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.votivo.result.api.PollResult;
import com.votivo.result.api.ResultController;
import com.votivo.result.service.ResultService;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ResultController.class)
class ResultServiceApplicationTests {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ResultService resultService;

    @Test
    void emptyResultHasStableJsonContract() throws Exception {
        String poll = "11111111-1111-4111-8111-111111111111";
        when(resultService.getResults(UUID.fromString(poll)))
                .thenReturn(new PollResult(UUID.fromString(poll), 0, List.of()));
        mvc.perform(get("/api/v1/polls/" + poll + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pollId").value(poll))
                .andExpect(jsonPath("$.totalVotes").value(0))
                .andExpect(jsonPath("$.questions").isEmpty());
    }

    @Test
    void invalidPollIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/polls/not-a-uuid/results"))
                .andExpect(status().isBadRequest());
    }
}
