package com.example.shadowrouter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.example.shadowrouter.client.InferenceClient;
import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.config.InferenceProperties;
import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.support.TestPayloads;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ShadowEvaluationServiceLoadShedTest {

    private ThreadPoolExecutor shadowExecutor;

    @AfterEach
    void tearDown() {
        if (shadowExecutor != null) {
            shadowExecutor.shutdownNow();
        }
    }

    @Test
    void shedsEvaluationWhenBoundedExecutorIsSaturated() throws Exception {
        // 1 worker + queue capacity 1 → third offer must be rejected.
        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        shadowExecutor = new ThreadPoolExecutor(
                1,
                1,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy());

        InferenceClient inferenceClient = mock(InferenceClient.class);
        when(inferenceClient.chatCompletion(any(), eq("candidate"), any()))
                .thenAnswer(invocation -> {
                    workerBlocked.countDown();
                    assertTrue(releaseWorker.await(5, TimeUnit.SECONDS));
                    return new InferenceResult(200, "{\"ok\":true}");
                });

        InferenceProperties properties = new InferenceProperties(
                "http://localhost",
                "key",
                "primary",
                "candidate",
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                1,
                1);

        ShadowMetrics metrics = new ShadowMetrics();
        MismatchTraceService mismatchTraceService = mock(MismatchTraceService.class);
        ShadowEvaluationService shadowEvaluationService = new ShadowEvaluationService(
                inferenceClient,
                properties,
                new OutputComparator(new ObjectMapper()),
                mismatchTraceService,
                metrics,
                shadowExecutor);

        ObjectNode payload = TestPayloads.chatPayload("hello");
        InferenceResult primaryResult = new InferenceResult(200, "{\"ok\":true}");

        CompletableFuture<InferenceResult> first = new CompletableFuture<>();
        assertTrue(shadowEvaluationService.submitEvaluation("r1", payload, first));
        first.complete(primaryResult);
        assertTrue(workerBlocked.await(5, TimeUnit.SECONDS));

        CompletableFuture<InferenceResult> second = new CompletableFuture<>();
        assertTrue(shadowEvaluationService.submitEvaluation("r2", payload, second));
        second.complete(primaryResult);

        CompletableFuture<InferenceResult> third = new CompletableFuture<>();
        assertFalse(shadowEvaluationService.submitEvaluation("r3", payload, third));

        assertEquals(1, metrics.snapshot().shadowEvaluationsShed());
        verify(inferenceClient, times(1)).chatCompletion(any(), eq("candidate"), any());

        releaseWorker.countDown();
    }
}
