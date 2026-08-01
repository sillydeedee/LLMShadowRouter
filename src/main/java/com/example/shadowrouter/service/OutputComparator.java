package com.example.shadowrouter.service;

import java.util.Optional;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Compares primary and candidate model outputs with two heuristics:
 *
 * <ol>
 *   <li>Both response bodies must be valid, parseable JSON.</li>
 *   <li>Extracted {@code action} values must match exactly.</li>
 * </ol>
 *
 * {@code action} is taken from a top-level field when present; otherwise from
 * JSON embedded in OpenAI-style {@code choices[0].message.content}.
 */
@Component
public class OutputComparator {

    private final ObjectMapper objectMapper;

    public OutputComparator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ComparisonResult compare(String primaryBody, String candidateBody) {
        Optional<JsonNode> primaryJson = parseJson(primaryBody);
        Optional<JsonNode> candidateJson = parseJson(candidateBody);

        boolean bothValidJson = primaryJson.isPresent() && candidateJson.isPresent();
        if (!bothValidJson) {
            return new ComparisonResult(false, false, null, null);
        }

        Optional<String> primaryAction = extractAction(primaryJson.get());
        Optional<String> candidateAction = extractAction(candidateJson.get());

        boolean exactActionMatch = primaryAction.isPresent()
                && candidateAction.isPresent()
                && primaryAction.get().equals(candidateAction.get());

        return new ComparisonResult(
                true,
                exactActionMatch,
                primaryAction.orElse(null),
                candidateAction.orElse(null));
    }

    private Optional<JsonNode> parseJson(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readTree(body));
        } catch (JacksonException ignored) {
            // Heuristic #1 failed: body is not valid JSON.
            return Optional.empty();
        }
    }

    /**
     * Resolves {@code action} from either the root object or assistant content JSON.
     */
    private Optional<String> extractAction(JsonNode root) {
        if (root.hasNonNull("action")) {
            return Optional.of(asComparableString(root.get("action")));
        }

        JsonNode content = root.path("choices")
                .path(0)
                .path("message")
                .path("content");

        if (!content.isTextual()) {
            return Optional.empty();
        }

        // Models often wrap JSON in ```json ... ``` fences.
        Optional<JsonNode> contentJson = parseJson(stripCodeFences(content.asText()));
        if (contentJson.isEmpty() || !contentJson.get().hasNonNull("action")) {
            return Optional.empty();
        }

        return Optional.of(asComparableString(contentJson.get().get("action")));
    }

    /** Removes surrounding markdown code fences when present. */
    private static String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }

        String withoutOpenFence = trimmed.substring(firstNewline + 1);
        int closeFence = withoutOpenFence.lastIndexOf("```");
        if (closeFence >= 0) {
            return withoutOpenFence.substring(0, closeFence).trim();
        }

        return withoutOpenFence.trim();
    }

    /** Normalizes textual and non-textual JSON nodes for exact string comparison. */
    private static String asComparableString(JsonNode node) {
        return node.isTextual() ? node.asText() : node.toString();
    }

    public record ComparisonResult(
            boolean bothValidJson,
            boolean exactActionMatch,
            String primaryAction,
            String candidateAction) {
    }
}
