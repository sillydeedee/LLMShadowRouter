package com.example.shadowrouter.exception;

/**
 * Failure while calling the candidate model during background shadow evaluation.
 */
public class CandidateInferenceException extends Exception {

    public CandidateInferenceException(String message, Throwable cause) {
        super(message, cause);
    }

    public static CandidateInferenceException from(Throwable cause) {
        return new CandidateInferenceException("candidate model call failed", cause);
    }
}
