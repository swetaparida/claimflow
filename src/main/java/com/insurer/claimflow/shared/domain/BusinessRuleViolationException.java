package com.insurer.claimflow.shared.domain;

/**
 * Raised when a well-formed request violates a business rule (e.g. amount exceeds coverage).
 * Mapped to HTTP 422 Unprocessable Entity.
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String code, String message) {
        super(code, message);
    }
}
