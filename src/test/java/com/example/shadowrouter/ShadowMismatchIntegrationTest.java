package com.example.shadowrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shadowrouter.client.InferenceClient;
import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.support.TestPayloads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ShadowMismatchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShadowMetrics metrics;

    @MockitoBean
    private InferenceClient inferenceClient;

    @Test
    void recordsNonMatchingActionsWithoutAffectingChatResponse() throws Exception {
        when(inferenceClient.chatCompletion(any(), eq("primary-test-model"), any()))
                .thenReturn(new InferenceResult(200, TestPayloads.openaiCompletion("retry")));
        when(inferenceClient.chatCompletion(any(), eq("candidate-test-model"), any()))
                .thenReturn(new InferenceResult(200, TestPayloads.openaiCompletion("abort")));

        long comparisonsBefore = metrics.snapshot().comparisonsEvaluated();
        long matchesBefore = metrics.snapshot().exactActionMatches();

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messages": [
                                    {"role": "user", "content": "Choose an action"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        verify(inferenceClient, timeout(5_000).atLeastOnce())
                .chatCompletion(any(), eq("candidate-test-model"), any());

        long deadline = System.currentTimeMillis() + 5_000;
        while (metrics.snapshot().comparisonsEvaluated() <= comparisonsBefore
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        assertThat(metrics.snapshot().comparisonsEvaluated()).isGreaterThan(comparisonsBefore);
        assertThat(metrics.snapshot().exactActionMatches()).isEqualTo(matchesBefore);
        assertThat(metrics.snapshot().exactMatchRatePercentage()).isLessThan(100.0);
    }
}
