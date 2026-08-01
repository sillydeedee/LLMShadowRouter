package com.example.shadowrouter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shadowrouter.client.InferenceClient;
import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.support.TestPayloads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShadowMetrics metrics;

    @MockitoBean
    private InferenceClient inferenceClient;

    @BeforeEach
    void setUp() throws Exception {
        when(inferenceClient.chatCompletion(any(), eq("primary-test-model"), any()))
                .thenReturn(new InferenceResult(200, TestPayloads.openaiCompletion("retry")));
        when(inferenceClient.chatCompletion(any(), eq("candidate-test-model"), any()))
                .thenReturn(new InferenceResult(200, TestPayloads.openaiCompletion("retry")));
    }

    @Test
    void chatEndpointReturnsPrimaryResponseAndTriggersShadowEvaluation() throws Exception {
        long requestsBefore = metrics.snapshot().totalRequestsProcessed();
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
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.choices[0].message.content").value("{\"action\":\"retry\"}"));

        verify(inferenceClient, atLeastOnce())
                .chatCompletion(any(), eq("primary-test-model"), any());
        verify(inferenceClient, timeout(5_000).atLeastOnce())
                .chatCompletion(any(), eq("candidate-test-model"), any());

        assertThat(metrics.snapshot().totalRequestsProcessed()).isGreaterThan(requestsBefore);

        // Wait for background comparison to land in metrics.
        long deadline = System.currentTimeMillis() + 5_000;
        while (metrics.snapshot().comparisonsEvaluated() <= comparisonsBefore
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(metrics.snapshot().comparisonsEvaluated()).isGreaterThan(comparisonsBefore);
        assertThat(metrics.snapshot().exactActionMatches()).isGreaterThan(matchesBefore);
    }

    @Test
    void metricsEndpointExposesCounters() throws Exception {
        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequestsProcessed").exists())
                .andExpect(jsonPath("$.shadowErrorsOrTimeouts").exists())
                .andExpect(jsonPath("$.shadowEvaluationsShed").exists())
                .andExpect(jsonPath("$.exactMatchRatePercentage").exists());
    }

    @Test
    void chatEndpointValidatesMessages() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "payload must contain a non-empty 'messages' array"));
    }
}
