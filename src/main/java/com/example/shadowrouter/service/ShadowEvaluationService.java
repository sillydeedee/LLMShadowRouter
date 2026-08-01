package com.example.shadowrouter.service;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import com.example.shadowrouter.client.InferenceClient;
import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.config.InferenceProperties;
import com.example.shadowrouter.config.ShadowExecutorConfig;
import com.example.shadowrouter.exception.CandidateInferenceException;
import com.example.shadowrouter.exception.PrimaryResultUnavailableException;
import com.example.shadowrouter.metrics.ShadowMetrics;
import com.example.shadowrouter.service.OutputComparator.ComparisonResult;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Runs candidate-model evaluation and primary-vs-candidate comparison
 * on a bounded background executor.
 *
 * Failures, timeouts, and load-shedding here never affect the chat response.
 */
@Service
public class ShadowEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ShadowEvaluationService.class);

    private final InferenceClient inferenceClient;
    private final InferenceProperties properties;
    private final OutputComparator outputComparator;
    private final ShadowMetrics metrics;
    private final ExecutorService shadowExecutor;

    public ShadowEvaluationService(
            InferenceClient inferenceClient,
            InferenceProperties properties,
            OutputComparator outputComparator,
            ShadowMetrics metrics,
            @Qualifier(ShadowExecutorConfig.SHADOW_EXECUTOR) ExecutorService shadowExecutor) {
        this.inferenceClient = inferenceClient;
        this.properties = properties;
        this.outputComparator = outputComparator;
        this.metrics = metrics;
        this.shadowExecutor = shadowExecutor;
    }

    /**
     * Offers a background candidate evaluation to the bounded executor.
     *
     * @param primaryResultFuture completed by {@link ChatService} with the primary response
     * @return {@code true} if accepted, {@code false} if load-shed
     */
    public boolean submitEvaluation(
            String requestId,
            ObjectNode payload,
            CompletableFuture<InferenceResult> primaryResultFuture) {

        try {
            shadowExecutor.execute(
                    () -> evaluateAndCompare(requestId, payload, primaryResultFuture));
            return true;
        } catch (RejectedExecutionException rejected) {
            metrics.recordShadowEvaluationShed();
            log.warn(
                    "requestId={} shadow evaluation shed (bounded queue saturated; "
                            + "maxConcurrency={} queueCapacity={})",
                    requestId,
                    properties.shadowMaxConcurrency(),
                    properties.shadowQueueCapacity());
            return false;
        }
    }

    /**
     * Calls the candidate model, waits for the primary result, then compares outputs.
     */
    private void evaluateAndCompare(
            String requestId,
            ObjectNode payload,
            CompletableFuture<InferenceResult> primaryResultFuture) {

        long startNanos = System.nanoTime();
        InferenceResult candidateResult;

        try {
            candidateResult = callCandidateModel(payload);
            log.info(
                    "requestId={} candidate model={} status={} latencyMs={}",
                    requestId,
                    properties.candidateModel(),
                    candidateResult.statusCode(),
                    elapsedMs(startNanos));
        } catch (CandidateInferenceException exception) {
            metrics.recordShadowErrorOrTimeout();
            log.warn(
                    "requestId={} candidate model={} failed after {}ms: {}",
                    requestId,
                    properties.candidateModel(),
                    elapsedMs(startNanos),
                    exception.toString());
            return;
        }

        InferenceResult primaryResult = awaitPrimaryResult(requestId, primaryResultFuture);
        if (primaryResult == null) {
            return;
        }

        ComparisonResult comparison = outputComparator.compare(
                primaryResult.body(),
                candidateResult.body());

        metrics.recordComparison(comparison.exactActionMatch());

        log.info(
                "requestId={} comparison bothValidJson={} exactActionMatch={} primaryAction={} candidateAction={}",
                requestId,
                comparison.bothValidJson(),
                comparison.exactActionMatch(),
                comparison.primaryAction(),
                comparison.candidateAction());
    }

    private InferenceResult callCandidateModel(ObjectNode payload) throws CandidateInferenceException {
        try {
            return inferenceClient.chatCompletion(
                    payload,
                    properties.candidateModel(),
                    properties.candidateTimeout());
        } catch (HttpTimeoutException timeout) {
            throw CandidateInferenceException.from(timeout);
        } catch (IOException ioException) {
            throw CandidateInferenceException.from(ioException);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw CandidateInferenceException.from(interrupted);
        }
    }

    private InferenceResult awaitPrimaryResult(
            String requestId,
            CompletableFuture<InferenceResult> primaryResultFuture) {

        try {
            return primaryResultFuture.join();
        } catch (CompletionException | CancellationException exception) {
            PrimaryResultUnavailableException unavailable = new PrimaryResultUnavailableException(
                    "primary result unavailable for comparison",
                    exception.getCause() != null ? exception.getCause() : exception);
            log.warn(
                    "requestId={} skipping comparison; {}",
                    requestId,
                    unavailable.toString());
            return null;
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
