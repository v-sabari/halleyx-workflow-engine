package com.halleyx.workflow_engine.exception;

/**
 * Thrown when a business rule is violated (e.g. executing an inactive workflow,
 * approving an execution not in WAITING_FOR_APPROVAL state, retry limit exceeded).
 *
 * Maps to HTTP 400 via GlobalExceptionHandler's RuntimeException handler.
 * Separated from ResourceNotFoundException so call-sites are self-documenting.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
