package com.insurer.claimflow.shared.domain;

/**
 * Raised when a requested resource does not exist. Mapped to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, Object id) {
        super("RESOURCE_NOT_FOUND", "%s %s not found".formatted(resource, id));
    }
}
