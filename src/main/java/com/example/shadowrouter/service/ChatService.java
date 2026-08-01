package com.example.shadowrouter.service;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;

import com.example.shadowrouter.client.InferenceClient;
import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.config.InferenceProperties;
import com.example.shadowrouter.exception.PrimaryInferenceException;
import com.example.shadowrouter.exception.ShadowOfferFailedException;
import com.example.shadowrouter.metrics.ShadowMetrics;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles the user-facing chat path: call the primary model and return its response.
 *
 * After optionally kicking off background candidate evaluation (non-blocking offer),
 * this service waits only on the primary inference call.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final InferenceClient inferenceClient;
    private final InferenceProperties properties;
    private final ShadowEvaluationService shadowEvaluationService;
    private final ShadowRoutingConfig shadowRoutingConfig;
    private final ShadowMetrics metrics;

    public ChatService(
            InferenceClient inferenceClient,
            InferenceProperties properties,
            ShadowEvaluationService shadowEvaluationService,
            ShadowRoutingConfig shadowRoutingConfig,
            ShadowMetrics metrics) {
        this.inferenceClient = inferenceClient;
        this.properties = properties;
        this.shadowEvaluationService = shadowEvaluationService;
        this.shadowRoutingConfig = shadowRoutingConfig;
        this.metrics = metrics;
    }

    /**
     * Completes a chat request for the caller using the primary model.
     *
     * Also may offer the same payload to {@link ShadowEvaluationService} for
     * asynchronous candidate evaluation, subject to the runtime routing percentage.
     */
    public InferenceResult completeChat(String requestId, ObjectNode payload)
            throws PrimaryInferenceException {

        // Share primary output with the background evaluator once it is ready.
        CompletableFuture<InferenceResult> primaryResultFuture = new CompletableFuture<>();
        boolean shadowAccepted = offerShadowEvaluation(requestId, payload, primaryResultFuture);

        long startNanos = System.nanoTime();

        try {
            InferenceResult primaryResult = inferenceClient.chatCompletion(
                    payload,
                    properties.primaryModel(),
                    properties.primaryTimeout());

            metrics.recordRequestProcessed();

            if (shadowAccepted) {
                primaryResultFuture.complete(primaryResult);
            }

            log.info(
                    "requestId={} primary model={} status={} latencyMs={}",
                    requestId,
                    properties.primaryModel(),
                    primaryResult.statusCode(),
                    elapsedMs(startNanos));

            return primaryResult;
        } catch (HttpTimeoutException timeout) {
            failPrimary(shadowAccepted, primaryResultFuture, timeout);
            throw PrimaryInferenceException.timedOut(timeout);
        } catch (IOException ioException) {
            failPrimary(shadowAccepted, primaryResultFuture, ioException);
            throw PrimaryInferenceException.unreachable(ioException);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failPrimary(shadowAccepted, primaryResultFuture, interrupted);
            throw PrimaryInferenceException.interrupted(interrupted);
        } catch (Exception exception) {
            failPrimary(shadowAccepted, primaryResultFuture, exception);
            throw PrimaryInferenceException.unexpected(exception);
        }
    }

    /**
     * Shadow offer must never fail the user-facing primary call.
     * Applies runtime routing percentage first, then bounded-executor load shedding.
     */
    private boolean offerShadowEvaluation(
            String requestId,
            ObjectNode payload,
            CompletableFuture<InferenceResult> primaryResultFuture) {

        if (!shadowRoutingConfig.shouldMirrorRequest()) {
            metrics.recordShadowRoutingSkipped();
            log.debug(
                    "requestId={} shadow mirroring skipped (routingPercentage={})",
                    requestId,
                    shadowRoutingConfig.getShadowRoutingPercentage());
            return false;
        }

        try {
            return shadowEvaluationService.submitEvaluation(
                    requestId,
                    payload.deepCopy(),
                    primaryResultFuture);
        } catch (Exception exception) {
            ShadowOfferFailedException offerFailed = new ShadowOfferFailedException(
                    "unexpected failure offering shadow evaluation",
                    exception);
            log.warn(
                    "requestId={} {}; continuing with primary only",
                    requestId,
                    offerFailed.toString());
            return false;
        }
    }

    private void failPrimary(
            boolean shadowAccepted,
            CompletableFuture<InferenceResult> primaryResultFuture,
            Exception cause) {

        metrics.recordRequestProcessed();
        if (shadowAccepted) {
            primaryResultFuture.completeExceptionally(cause);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
