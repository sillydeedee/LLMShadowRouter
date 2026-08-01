package com.example.shadowrouter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.example.shadowrouter.client.InferenceClient;
import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.config.InferenceProperties;
import com.example.shadowrouter.exception.PrimaryInferenceException;
import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.support.TestPayloads;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private InferenceClient inferenceClient;

    @Mock
    private ShadowEvaluationService shadowEvaluationService;

    @Mock
    private ShadowRoutingConfig shadowRoutingConfig;

    private ShadowMetrics metrics;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
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
        chatService = new ChatService(
                inferenceClient,
                properties,
                shadowEvaluationService,
                shadowRoutingConfig,
                metrics);
    }

    @Test
    void completeChatReturnsPrimaryResultAndCompletesShadowFuture() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");
        InferenceResult primary = new InferenceResult(200, "{\"answer\":1}");

        when(shadowRoutingConfig.shouldMirrorRequest()).thenReturn(true);
        AtomicReference<CompletableFuture<InferenceResult>> capturedFuture = new AtomicReference<>();
        when(shadowEvaluationService.submitEvaluation(eq("req-1"), any(), any()))
                .thenAnswer(invocation -> {
                    capturedFuture.set(invocation.getArgument(2));
                    return true;
                });
        when(inferenceClient.chatCompletion(eq(payload), eq("primary-model"), any()))
                .thenReturn(primary);

        InferenceResult result = chatService.completeChat("req-1", payload);

        assertEquals(primary, result);
        assertEquals(1, metrics.snapshot().totalRequestsProcessed());
        assertTrue(capturedFuture.get().isDone());
        assertEquals(primary, capturedFuture.get().get());

        ArgumentCaptor<ObjectNode> shadowPayload = ArgumentCaptor.forClass(ObjectNode.class);
        verify(shadowEvaluationService).submitEvaluation(eq("req-1"), shadowPayload.capture(), any());
        assertEquals("hello", shadowPayload.getValue().path("messages").path(0).path("content").asText());
    }

    @Test
    void completeChatStillCountsRequestWhenPrimaryFailsAndFailsShadowFuture() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");
        AtomicReference<CompletableFuture<InferenceResult>> capturedFuture = new AtomicReference<>();

        when(shadowRoutingConfig.shouldMirrorRequest()).thenReturn(true);
        when(shadowEvaluationService.submitEvaluation(eq("req-2"), any(), any()))
                .thenAnswer(invocation -> {
                    capturedFuture.set(invocation.getArgument(2));
                    return true;
                });
        when(inferenceClient.chatCompletion(any(), eq("primary-model"), any()))
                .thenThrow(new IOException("upstream down"));

        PrimaryInferenceException thrown = assertThrows(
                PrimaryInferenceException.class,
                () -> chatService.completeChat("req-2", payload));

        assertEquals("primary model is unreachable", thrown.getMessage());
        assertEquals(1, metrics.snapshot().totalRequestsProcessed());
        assertTrue(capturedFuture.get().isCompletedExceptionally());
    }

    @Test
    void completeChatDoesNotCompleteFutureWhenShadowWasShed() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");
        InferenceResult primary = new InferenceResult(200, "{\"ok\":true}");

        when(shadowRoutingConfig.shouldMirrorRequest()).thenReturn(true);
        when(shadowEvaluationService.submitEvaluation(eq("req-3"), any(), any())).thenReturn(false);
        when(inferenceClient.chatCompletion(any(), eq("primary-model"), any())).thenReturn(primary);

        InferenceResult result = chatService.completeChat("req-3", payload);

        assertEquals(primary, result);
        assertEquals(1, metrics.snapshot().totalRequestsProcessed());
    }

    @Test
    void completeChatSkipsShadowWhenRoutingPercentageExcludesRequest() throws Exception {
        ObjectNode payload = TestPayloads.chatPayload("hello");
        InferenceResult primary = new InferenceResult(200, "{\"ok\":true}");

        when(shadowRoutingConfig.shouldMirrorRequest()).thenReturn(false);
        when(shadowRoutingConfig.getShadowRoutingPercentage()).thenReturn(0);
        when(inferenceClient.chatCompletion(any(), eq("primary-model"), any())).thenReturn(primary);

        InferenceResult result = chatService.completeChat("req-4", payload);

        assertEquals(primary, result);
        assertEquals(1, metrics.snapshot().shadowRoutingSkipped());
        verify(shadowEvaluationService, never()).submitEvaluation(any(), any(), any());
    }
}
