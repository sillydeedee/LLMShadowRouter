package com.example.shadowrouter.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.example.shadowrouter.config.InferenceProperties;
import com.example.shadowrouter.support.TestPayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InferenceClientIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] response = "{\"id\":\"cmp_1\",\"choices\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void postsOpenAiCompatiblePayloadWithModelOverrideAndAuth() throws Exception {
        InferenceProperties properties = new InferenceProperties(
                baseUrl,
                "secret-key",
                "primary-model",
                "candidate-model",
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                2,
                4);

        InferenceClient client = new InferenceClient(objectMapper, properties);
        var payload = TestPayloads.chatPayload("ping");
        payload.put("model", "caller-supplied-model");
        payload.put("stream", true);

        InferenceClient.InferenceResult result = client.chatCompletion(
                payload,
                "forced-model",
                Duration.ofSeconds(5));

        assertEquals(201, result.statusCode());
        assertEquals("{\"id\":\"cmp_1\",\"choices\":[]}", result.body());
        assertEquals("Bearer secret-key", lastAuth.get());

        JsonNode sent = objectMapper.readTree(lastBody.get());
        assertEquals("forced-model", sent.path("model").asText());
        assertTrue(sent.path("stream").isBoolean());
        assertEquals(false, sent.path("stream").asBoolean());
        assertEquals("ping", sent.path("messages").path(0).path("content").asText());
    }
}
