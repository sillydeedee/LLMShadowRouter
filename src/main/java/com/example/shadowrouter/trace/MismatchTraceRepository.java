package com.example.shadowrouter.trace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.example.shadowrouter.config.RuntimeShadowProperties;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * SQLite-backed store for mismatched shadow evaluations.
 */
@Repository
public class MismatchTraceRepository {

    private static final Logger log = LoggerFactory.getLogger(MismatchTraceRepository.class);

    private final String jdbcUrl;

    public MismatchTraceRepository(RuntimeShadowProperties properties) {
        Path dbPath = Path.of(properties.sqlitePath()).toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    @PostConstruct
    void initialize() {
        try {
            Path dbFile = Path.of(jdbcUrl.substring("jdbc:sqlite:".length()));
            Path parent = dbFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (Connection connection = open();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS mismatch_traces (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            request_id TEXT NOT NULL,
                            created_at TEXT NOT NULL,
                            request_payload TEXT NOT NULL,
                            primary_body TEXT NOT NULL,
                            candidate_body TEXT NOT NULL,
                            primary_action TEXT,
                            candidate_action TEXT
                        )
                        """);
            }
            log.info("mismatch trace sqlite ready at {}", jdbcUrl);
        } catch (IOException | SQLException exception) {
            throw new IllegalStateException("failed to initialize mismatch sqlite database", exception);
        }
    }

    public void insert(MismatchTrace trace) throws SQLException {
        String sql = """
                INSERT INTO mismatch_traces (
                    request_id, created_at, request_payload, primary_body,
                    candidate_body, primary_action, candidate_action
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trace.requestId());
            statement.setString(2, trace.createdAt().toString());
            statement.setString(3, trace.requestPayload());
            statement.setString(4, trace.primaryBody());
            statement.setString(5, trace.candidateBody());
            statement.setString(6, trace.primaryAction());
            statement.setString(7, trace.candidateAction());
            statement.executeUpdate();
        }
    }

    public List<MismatchTrace> findRecent(int limit) throws SQLException {
        String sql = """
                SELECT id, request_id, created_at, request_payload, primary_body,
                       candidate_body, primary_action, candidate_action
                FROM mismatch_traces
                ORDER BY id DESC
                LIMIT ?
                """;

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                List<MismatchTrace> traces = new ArrayList<>();
                while (rs.next()) {
                    traces.add(new MismatchTrace(
                            rs.getLong("id"),
                            rs.getString("request_id"),
                            Instant.parse(rs.getString("created_at")),
                            rs.getString("request_payload"),
                            rs.getString("primary_body"),
                            rs.getString("candidate_body"),
                            rs.getString("primary_action"),
                            rs.getString("candidate_action")));
                }
                return traces;
            }
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
