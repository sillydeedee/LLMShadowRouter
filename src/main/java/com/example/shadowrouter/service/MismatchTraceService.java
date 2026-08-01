package com.example.shadowrouter.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.trace.MismatchTrace;
import com.example.shadowrouter.trace.MismatchTraceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Asynchronously persists mismatched primary/candidate payloads to SQLite.
 *
 * Writes are offered to a single-threaded bounded queue so shadow evaluation
 * workers are not blocked on disk I/O, and bursts cannot grow unbounded.
 */
@Service
public class MismatchTraceService {

    private static final Logger log = LoggerFactory.getLogger(MismatchTraceService.class);
    private static final int WRITE_QUEUE_CAPACITY = 256;

    private final MismatchTraceRepository repository;
    private final ObjectMapper objectMapper;
    private final ShadowMetrics metrics;
    private final ExecutorService writeExecutor;

    public MismatchTraceService(
            MismatchTraceRepository repository,
            ObjectMapper objectMapper,
            ShadowMetrics metrics) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.writeExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(WRITE_QUEUE_CAPACITY),
                Thread.ofVirtual().name("mismatch-trace-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Enqueues a mismatch for durable storage. Never throws to callers.
     */
    public void recordMismatchAsync(
            String requestId,
            ObjectNode requestPayload,
            String primaryBody,
            String candidateBody,
            String primaryAction,
            String candidateAction) {

        final String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException exception) {
            log.warn("requestId={} failed to serialize request payload for trace: {}",
                    requestId, exception.toString());
            return;
        }

        MismatchTrace trace = new MismatchTrace(
                null,
                requestId,
                Instant.now(),
                payloadJson,
                primaryBody,
                candidateBody,
                primaryAction,
                candidateAction);

        try {
            writeExecutor.execute(() -> persist(trace));
        } catch (RejectedExecutionException rejected) {
            metrics.recordMismatchTraceShed();
            log.warn(
                    "requestId={} mismatch trace shed (sqlite write queue saturated)",
                    requestId);
        }
    }

    public List<MismatchTrace> recentTraces(int limit) {
        try {
            return repository.findRecent(limit);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read mismatch traces", exception);
        }
    }

    private void persist(MismatchTrace trace) {
        try {
            repository.insert(trace);
            metrics.recordMismatchTracePersisted();
            log.info(
                    "requestId={} persisted mismatch trace primaryAction={} candidateAction={}",
                    trace.requestId(),
                    trace.primaryAction(),
                    trace.candidateAction());
        } catch (SQLException exception) {
            metrics.recordMismatchTraceError();
            log.warn(
                    "requestId={} failed to persist mismatch trace: {}",
                    trace.requestId(),
                    exception.toString());
        }
    }

    @PreDestroy
    void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writeExecutor.shutdownNow();
        }
    }
}
