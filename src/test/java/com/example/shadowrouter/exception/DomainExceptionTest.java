package com.example.shadowrouter.exception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void primaryInferenceExceptionDetectsTimeoutCause() {
        PrimaryInferenceException timeout = PrimaryInferenceException.timedOut(
                new HttpTimeoutException("timed out"));
        PrimaryInferenceException unreachable = PrimaryInferenceException.unreachable(
                new IOException("down"));

        assertTrue(timeout.isTimeout());
        assertFalse(timeout.isInterrupted());
        assertFalse(unreachable.isTimeout());
    }
}
