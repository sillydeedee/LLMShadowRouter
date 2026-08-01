package com.example.shadowrouter.exception;

/**
 * Raised when shadow evaluation cannot obtain the primary model result
 * (primary failed, was cancelled, or the future completed exceptionally).
 */
public class PrimaryResultUnavailableException extends RuntimeException {

    public PrimaryResultUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
