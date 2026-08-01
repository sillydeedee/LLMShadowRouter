package com.example.shadowrouter.trace;

import java.time.Instant;

/**
 * One persisted primary-vs-candidate mismatch for debugging / visualization.
 */
public record MismatchTrace(
        Long id,
        String requestId,
        Instant createdAt,
        String requestPayload,
        String primaryBody,
        String candidateBody,
        String primaryAction,
        String candidateAction) {
}
