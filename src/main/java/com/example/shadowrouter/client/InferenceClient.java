package com.example.shadowrouter.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.example.shadowrouter.config.InferenceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

/**
 * Thin HTTP client for DigitalOcean's OpenAI-compatible chat completions API.
 *
 * Used for both primary and candidate calls; only model name and timeout differ.
 */
@Component
public class InferenceClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final InferenceProperties properties;

    public InferenceClient(ObjectMapper objectMapper, InferenceProperties properties) {
        this(
                objectMapper,
                properties,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build());
    }

    /** Visible for tests that supply a custom {@link HttpClient}. */
    InferenceClient(ObjectMapper objectMapper, InferenceProperties properties, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = httpClient;
    }

    /**
     * Forwards the chat payload to {@code /chat/completions}.
     *
     * Always overrides {@code model} and forces {@code stream=false} so primary
     * and candidate receive the same prompt shape with the configured model.
     */
    public InferenceResult chatCompletion(ObjectNode payload, String model, Duration timeout)
            throws IOException, InterruptedException {

        ObjectNode body = payload.deepCopy();
        body.put("model", model);
        body.put("stream", false);

        String requestJson = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.accessKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        return new InferenceResult(response.statusCode(), response.body());
    }

    /** Raw status code and JSON body returned by the inference API. */
    public record InferenceResult(int statusCode, String body) {
    }
}
