package com.example.shadowrouter.exception;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

/**
 * Failure while calling the primary model for the user-facing chat response.
 */
public class PrimaryInferenceException extends Exception {

    public PrimaryInferenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean isTimeout() {
        return getCause() instanceof HttpTimeoutException;
    }

    public boolean isInterrupted() {
        return getCause() instanceof InterruptedException;
    }

    public static PrimaryInferenceException timedOut(HttpTimeoutException cause) {
        return new PrimaryInferenceException("primary model timed out", cause);
    }

    public static PrimaryInferenceException unreachable(IOException cause) {
        return new PrimaryInferenceException("primary model is unreachable", cause);
    }

    public static PrimaryInferenceException interrupted(InterruptedException cause) {
        return new PrimaryInferenceException("primary model call was interrupted", cause);
    }
}
