package com.example.shadowrouter.controller;

import com.example.shadowrouter.exception.InvalidChatPayloadException;
import com.example.shadowrouter.exception.PrimaryInferenceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions from the primary chat path to clean JSON HTTP errors.
 *
 * Shadow-call failures never reach this handler; they are caught and logged
 * inside {@link com.example.shadowrouter.service.ShadowEvaluationService}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidChatPayloadException.class)
    public ResponseEntity<String> handleInvalidPayload(InvalidChatPayloadException exception) {
        return jsonError(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(PrimaryInferenceException.class)
    public ResponseEntity<String> handlePrimaryInferenceFailure(PrimaryInferenceException exception) {
        if (exception.isInterrupted()) {
            Thread.currentThread().interrupt();
        }

        if (exception.isTimeout()) {
            log.error("primary inference call timed out", exception);
            return jsonError(HttpStatus.GATEWAY_TIMEOUT, exception.getMessage());
        }

        log.error("primary inference call failed", exception);
        return jsonError(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    private static ResponseEntity<String> jsonError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + message + "\"}");
    }
}
