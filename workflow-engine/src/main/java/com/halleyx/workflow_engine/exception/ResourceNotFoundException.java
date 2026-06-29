package com.halleyx.workflow_engine.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource (Workflow, Step, Rule, Execution) does not
 * exist.  Maps to HTTP 404 via GlobalExceptionHandler.
 *
 * Using a dedicated exception instead of RuntimeException allows the handler
 * to return the semantically correct 404 status instead of 400.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " not found: " + id);
    }
}
