package com.example.shadowrouter.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the DigitalOcean serverless inference API, model selection,
 * and bounded shadow-evaluation concurrency.
 *
 * @param baseUrl               OpenAI-compatible base URL
 * @param accessKey             DigitalOcean model access key (Bearer token)
 * @param primaryModel          model that serves the user-facing response
 * @param candidateModel        model that receives the mirrored (shadow) payload
 * @param primaryTimeout        max wait for the primary before failing the request
 * @param candidateTimeout      max wait for a single background shadow call
 * @param shadowMaxConcurrency  max concurrent background shadow evaluations
 * @param shadowQueueCapacity   max queued shadow tasks before load shedding
 */
@ConfigurationProperties(prefix = "inference")
public record InferenceProperties(
        String baseUrl,
        String accessKey,
        String primaryModel,
        String candidateModel,
        Duration primaryTimeout,
        Duration candidateTimeout,
        int shadowMaxConcurrency,
        int shadowQueueCapacity) {
}
