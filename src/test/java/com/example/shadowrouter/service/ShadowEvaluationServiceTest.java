package com.example.shadowrouter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.example.shadowrouter.client.InferenceClient;
import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.config.InferenceProperties;
import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.support.TestPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShadowEvaluationServiceTest {

    @Mock
    private InferenceClient inferenceClient;

    @Mock
    private MismatchTraceService mismatchTraceService;

    private ExecutorService executor;
    private ShadowMetrics metrics;
    private ShadowEvaluationService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        metrics = new ShadowMetrics();
        InferenceProperties properties = new InferenceProperties(
                "http://localhost",
                "key",
                "primary-model",
                "candidate-model",
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                4,
                8);
        service = new ShadowEvaluationService(
                inferenceClient,
                properties,
                new OutputComparator(new ObjectMapper()),
                mismatchTraceService,
                metrics,
                executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void evaluateAndCompareRecordsExactActionMatch() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");
        CountDownLatch done = new CountDownLatch(1);

        when(inferenceClient.chatCompletion(any(), eq("candidate-model"), any()))
                .thenAnswer(invocation -> {
                    done.countDown();
                    return new InferenceResult(200, TestPayloads.openaiCompletion("retry"));
                });

        CompletableFuture<InferenceResult> primaryFuture = new CompletableFuture<>();
        assertTrue(service.submitEvaluation("req-1", payload, primaryFuture));
        primaryFuture.complete(new InferenceResult(200, TestPayloads.openaiCompletion("retry")));

        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitComparisons(1);

        assertEquals(1, metrics.snapshot().comparisonsEvaluated());
        assertEquals(1, metrics.snapshot().exactActionMatches());
        assertEquals(100.0, metrics.snapshot().exactMatchRatePercentage());
        verify(mismatchTraceService, never()).recordMismatchAsync(any(), any(), any(), any(), any(), any());
    }

    @Test
    void persistsMismatchWhenActionsDiffer() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");

        when(inferenceClient.chatCompletion(any(), eq("candidate-model"), any()))
                .thenReturn(new InferenceResult(200, TestPayloads.openaiCompletion("abort")));

        CompletableFuture<InferenceResult> primaryFuture = new CompletableFuture<>();
        assertTrue(service.submitEvaluation("req-mismatch", payload, primaryFuture));
        primaryFuture.complete(new InferenceResult(200, TestPayloads.openaiCompletion("retry")));

        verify(mismatchTraceService, timeout(5_000)).recordMismatchAsync(
                eq("req-mismatch"),
                any(),
                any(),
                any(),
                eq("retry"),
                eq("abort"));
        assertEquals(0, metrics.snapshot().exactActionMatches());
    }

    @Test
    void candidateFailureIncrementsShadowErrorCounter() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");
        CountDownLatch done = new CountDownLatch(1);

        when(inferenceClient.chatCompletion(any(), eq("candidate-model"), any()))
                .thenAnswer(invocation -> {
                    done.countDown();
                    throw new IOException("candidate timeout");
                });

        CompletableFuture<InferenceResult> primaryFuture = new CompletableFuture<>();
        assertTrue(service.submitEvaluation("req-2", payload, primaryFuture));
        primaryFuture.complete(new InferenceResult(200, TestPayloads.openaiCompletion("retry")));

        assertTrue(done.await(5, TimeUnit.SECONDS));
        awaitShadowErrors(1);

        assertEquals(1, metrics.snapshot().shadowErrorsOrTimeouts());
        assertEquals(0, metrics.snapshot().comparisonsEvaluated());
    }

    @Test
    void skipsComparisonWhenPrimaryFutureFails() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");
        CountDownLatch candidateStarted = new CountDownLatch(1);

        when(inferenceClient.chatCompletion(any(), eq("candidate-model"), any()))
                .thenAnswer(invocation -> {
                    candidateStarted.countDown();
                    return new InferenceResult(200, TestPayloads.openaiCompletion("retry"));
                });

        CompletableFuture<InferenceResult> primaryFuture = new CompletableFuture<>();
        assertTrue(service.submitEvaluation("req-3", payload, primaryFuture));
        primaryFuture.completeExceptionally(new IOException("primary failed"));

        assertTrue(candidateStarted.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);

        assertEquals(0, metrics.snapshot().comparisonsEvaluated());
        verify(inferenceClient).chatCompletion(any(), eq("candidate-model"), any());
        verify(inferenceClient, never()).chatCompletion(any(), eq("primary-model"), any());
        verify(mismatchTraceService, never())
                .recordMismatchAsync(any(), any(), any(), any(), any(), any());
    }

    private void awaitComparisons(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (metrics.snapshot().comparisonsEvaluated() < expected
                && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(expected, metrics.snapshot().comparisonsEvaluated());
    }

    private void awaitShadowErrors(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (metrics.snapshot().shadowErrorsOrTimeouts() < expected
                && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(expected, metrics.snapshot().shadowErrorsOrTimeouts());
    }
}
