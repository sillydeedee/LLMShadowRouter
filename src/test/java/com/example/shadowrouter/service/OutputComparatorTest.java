package com.example.shadowrouter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shadowrouter.service.OutputComparator.ComparisonResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutputComparatorTest {

    private OutputComparator comparator;

    @BeforeEach
    void setUp() {
        comparator = new OutputComparator(new ObjectMapper());
    }

    @Test
    void matchesWhenActionInMessageContentIsIdentical() {
        String primary = openaiBody("{\"action\":\"retry\"}");
        String candidate = openaiBody("{\"action\":\"retry\"}");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertTrue(result.bothValidJson());
        assertTrue(result.exactActionMatch());
        assertEquals("retry", result.primaryAction());
        assertEquals("retry", result.candidateAction());
    }

    @Test
    void doesNotMatchWhenActionsDiffer() {
        String primary = openaiBody("{\"action\":\"retry\"}");
        String candidate = openaiBody("{\"action\":\"abort\"}");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertTrue(result.bothValidJson());
        assertFalse(result.exactActionMatch());
    }

    @Test
    void invalidJsonFailsFirstHeuristic() {
        ComparisonResult result = comparator.compare("not-json", "{\"action\":\"retry\"}");

        assertFalse(result.bothValidJson());
        assertFalse(result.exactActionMatch());
    }

    @Test
    void stripsMarkdownFencesAroundContentJson() {
        String primary = openaiBody("```json\n{\"action\":\"continue\"}\n```");
        String candidate = openaiBody("{\"action\":\"continue\"}");

        ComparisonResult result = comparator.compare(primary, candidate);

        assertTrue(result.bothValidJson());
        assertTrue(result.exactActionMatch());
    }

    @Test
    void matchesTopLevelActionFields() {
        ComparisonResult result = comparator.compare(
                "{\"action\":\"scale_up\"}",
                "{\"action\":\"scale_up\",\"reason\":\"load\"}");

        assertTrue(result.bothValidJson());
        assertTrue(result.exactActionMatch());
        assertEquals("scale_up", result.primaryAction());
    }

    @Test
    void missingActionIsNotAnExactMatch() {
        ComparisonResult result = comparator.compare(
                openaiBody("{\"status\":\"ok\"}"),
                openaiBody("{\"action\":\"retry\"}"));

        assertTrue(result.bothValidJson());
        assertFalse(result.exactActionMatch());
    }

    private static String openaiBody(String content) {
        return """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": %s
                      }
                    }
                  ]
                }
                """.formatted(toJsonString(content));
    }

    private static String toJsonString(String content) {
        return "\"" + content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }
}
