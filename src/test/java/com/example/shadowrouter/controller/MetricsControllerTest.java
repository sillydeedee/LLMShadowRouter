package com.example.shadowrouter.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shadowrouter.metrics.ShadowMetrics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MetricsController.class)
@Import(ShadowMetrics.class)
class MetricsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShadowMetrics metrics;

    @Test
    void metricsReturnsRealtimeSnapshot() throws Exception {
        metrics.recordRequestProcessed();
        metrics.recordShadowEvaluationShed();
        metrics.recordComparison(true);

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequestsProcessed").value(1))
                .andExpect(jsonPath("$.shadowEvaluationsShed").value(1))
                .andExpect(jsonPath("$.comparisonsEvaluated").value(1))
                .andExpect(jsonPath("$.exactActionMatches").value(1))
                .andExpect(jsonPath("$.exactMatchRatePercentage").value(100.0));
    }
}
