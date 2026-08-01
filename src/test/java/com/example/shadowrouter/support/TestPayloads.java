package com.example.shadowrouter.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class TestPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestPayloads() {
    }

    public static ObjectNode chatPayload(String userContent) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.putArray("messages")
                .addObject()
                .put("role", "user")
                .put("content", userContent);
        return payload;
    }

    public static String openaiCompletion(String action) {
        return openaiCompletionWithContent("{\"action\":\"" + action + "\"}");
    }

    public static String openaiCompletionWithContent(String content) {
        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
        return """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      }
                    }
                  ]
                }
                """.formatted(escaped);
    }
}
