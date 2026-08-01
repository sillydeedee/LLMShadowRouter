package com.example.shadowrouter.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * In-memory, thread-safe counters backing {@code GET /metrics}.
 */
@Component
public class ShadowMetrics {

    private final AtomicLong totalRequestsProcessed = new AtomicLong();
    private final AtomicLong shadowErrorsOrTimeouts = new AtomicLong();
    private final AtomicLong shadowEvaluationsShed = new AtomicLong();
    private final AtomicLong comparisonsEvaluated = new AtomicLong();
    private final AtomicLong exactActionMatches = new AtomicLong();

    /** Increments once per {@code /v1/chat} attempt (success or primary failure). */
    public void recordRequestProcessed() {
        totalRequestsProcessed.incrementAndGet();
    }

    /** Increments when the background candidate call errors or times out. */
    public void recordShadowErrorOrTimeout() {
        shadowErrorsOrTimeouts.incrementAndGet();
    }

    /**
     * Increments when a shadow evaluation is dropped because the bounded
     * shadow executor is saturated (load shedding).
     */
    public void recordShadowEvaluationShed() {
        shadowEvaluationsShed.incrementAndGet();
    }

    /**
     * Records one completed primary-vs-candidate comparison.
     *
     * @param exactActionMatch whether extracted {@code action} values were identical
     */
    public void recordComparison(boolean exactActionMatch) {
        comparisonsEvaluated.incrementAndGet();
        if (exactActionMatch) {
            exactActionMatches.incrementAndGet();
        }
    }

    /** Point-in-time view of all counters, including computed match rate. */
    public MetricsSnapshot snapshot() {
        long evaluated = comparisonsEvaluated.get();
        long matches = exactActionMatches.get();

        double exactMatchRatePercentage = evaluated == 0
                ? 0.0
                : (matches * 100.0) / evaluated;

        return new MetricsSnapshot(
                totalRequestsProcessed.get(),
                shadowErrorsOrTimeouts.get(),
                shadowEvaluationsShed.get(),
                evaluated,
                matches,
                roundOneDecimal(exactMatchRatePercentage));
    }

    private static double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record MetricsSnapshot(
            long totalRequestsProcessed,
            long shadowErrorsOrTimeouts,
            long shadowEvaluationsShed,
            long comparisonsEvaluated,
            long exactActionMatches,
            double exactMatchRatePercentage) {
    }
}
