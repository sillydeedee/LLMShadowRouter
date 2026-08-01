package com.example.shadowrouter.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShadowMetricsTest {

    @Test
    void snapshotComputesExactMatchRateAndDefaultsToZero() {
        ShadowMetrics metrics = new ShadowMetrics();

        assertEquals(0.0, metrics.snapshot().exactMatchRatePercentage());

        metrics.recordRequestProcessed();
        metrics.recordShadowErrorOrTimeout();
        metrics.recordShadowEvaluationShed();
        metrics.recordComparison(true);
        metrics.recordComparison(false);

        ShadowMetrics.MetricsSnapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.totalRequestsProcessed());
        assertEquals(1, snapshot.shadowErrorsOrTimeouts());
        assertEquals(1, snapshot.shadowEvaluationsShed());
        assertEquals(2, snapshot.comparisonsEvaluated());
        assertEquals(1, snapshot.exactActionMatches());
        assertEquals(50.0, snapshot.exactMatchRatePercentage());
    }
}
