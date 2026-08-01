package com.example.shadowrouter.controller;

import java.util.UUID;

import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.exception.InvalidChatPayloadException;
import com.example.shadowrouter.exception.PrimaryInferenceException;
import com.example.shadowrouter.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point for chat requests.
 *
 * Validates the OpenAI-style payload, then delegates to {@link ChatService}
 * which returns the primary model response to the caller.
 */
@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(
            value = "/v1/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> chat(@RequestBody ObjectNode payload)
            throws PrimaryInferenceException {

        if (!hasNonEmptyMessages(payload)) {
            throw new InvalidChatPayloadException(
                    "payload must contain a non-empty 'messages' array");
        }

        // Correlates primary + shadow logs/metrics for this request.
        String requestId = UUID.randomUUID().toString();
        InferenceResult result = chatService.completeChat(requestId, payload);

        // Pass through upstream status and body unchanged.
        return ResponseEntity.status(result.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId)
                .body(result.body());
    }

    /**
     * OpenAI chat payloads must include a non-empty {@code messages} array.
     * Natural-language user text lives inside {@code messages[].content}.
     */
    private static boolean hasNonEmptyMessages(ObjectNode payload) {
        JsonNode messages = payload.get("messages");
        return messages != null && messages.isArray() && !messages.isEmpty();
    }
}
