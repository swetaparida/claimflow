package com.insurer.claimflow.shared.domain;

/**
 * Raised when an aggregate is asked to move to a state that its lifecycle does not permit.
 * Mapped to HTTP 409 Conflict.
 */
public class InvalidStateTransitionException extends DomainException {

    private final String currentState;
    private final String targetState;

    public InvalidStateTransitionException(String aggregate, Object aggregateId, String currentState, String targetState) {
        super("INVALID_STATE_TRANSITION",
                "%s %s cannot transition from %s to %s".formatted(aggregate, aggregateId, currentState, targetState));
        this.currentState = currentState;
        this.targetState = targetState;
    }

    public String getCurrentState() {
        return currentState;
    }

    public String getTargetState() {
        return targetState;
    }
}
