package com.example.shadowrouter.controller;

import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.metrics.ShadowMetrics.MetricsSnapshot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes a real-time summary of shadow-routing observability counters.
 */
@RestController
public class MetricsController {

    private final ShadowMetrics metrics;

    public MetricsController(ShadowMetrics metrics) {
        this.metrics = metrics;
    }

    /**
     * Returns totals for requests processed, shadow failures/timeouts,
     * load-shed shadow evaluations, and the exact {@code action} match rate.
     */
    @GetMapping("/metrics")
    public MetricsSnapshot metrics() {
        return metrics.snapshot();
    }
}
