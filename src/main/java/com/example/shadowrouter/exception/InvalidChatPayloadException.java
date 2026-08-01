package com.example.shadowrouter.exception;

/**
 * Thrown when a {@code /v1/chat} request body fails validation
 * (for example, missing or empty {@code messages}).
 */
public class InvalidChatPayloadException extends RuntimeException {

    public InvalidChatPayloadException(String message) {
        super(message);
    }
}
