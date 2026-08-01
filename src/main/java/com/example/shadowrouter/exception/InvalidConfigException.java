package com.example.shadowrouter.exception;

/**
 * Thrown when a runtime configuration update fails validation.
 */
public class InvalidConfigException extends RuntimeException {

    public InvalidConfigException(String message) {
        super(message);
    }
}
