package com.insurer.claimflow.shared.domain;

/**
 * Base type for all business exceptions raised by domain and application code.
 * Kept free of framework dependencies so it can be thrown from the pure domain layer.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
