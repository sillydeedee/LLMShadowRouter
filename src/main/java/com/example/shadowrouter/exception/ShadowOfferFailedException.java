package com.example.shadowrouter.exception;

/**
 * Unexpected failure while offering work to the bounded shadow executor.
 * Distinct from load shedding ({@link java.util.concurrent.RejectedExecutionException}),
 * which is handled as a non-exceptional {@code false} return.
 */
public class ShadowOfferFailedException extends RuntimeException {

    public ShadowOfferFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
