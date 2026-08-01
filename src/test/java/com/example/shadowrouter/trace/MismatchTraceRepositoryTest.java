package com.example.shadowrouter.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.shadowrouter.config.RuntimeShadowProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MismatchTraceRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void insertsAndReadsRecentMismatchTraces() throws Exception {
        Path dbPath = tempDir.resolve("mismatches-" + UUID.randomUUID() + ".db");
        MismatchTraceRepository repository = new MismatchTraceRepository(
                new RuntimeShadowProperties(100, dbPath.toString()));
        repository.initialize();

        repository.insert(new MismatchTrace(
                null,
                "req-1",
                Instant.parse("2026-08-01T10:00:00Z"),
                "{\"messages\":[]}",
                "{\"action\":\"retry\"}",
                "{\"action\":\"abort\"}",
                "retry",
                "abort"));

        List<MismatchTrace> traces = repository.findRecent(10);

        assertEquals(1, traces.size());
        assertEquals("req-1", traces.getFirst().requestId());
        assertEquals("retry", traces.getFirst().primaryAction());
        assertEquals("abort", traces.getFirst().candidateAction());
        assertFalse(Files.notExists(dbPath));
    }
}
