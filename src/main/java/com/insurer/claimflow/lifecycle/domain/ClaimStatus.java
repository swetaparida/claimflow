package com.insurer.claimflow.lifecycle.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle state machine.
 *
 * <pre>
 * REPORTED → ASSIGNED → UNDER_REVIEW → APPROVED → SETTLED
 *                           │  ↑
 *                           │  └── INFO_REQUIRED
 *                           ├────→ INFO_REQUIRED
 *                           └────→ REJECTED
 * </pre>
 */
public enum ClaimStatus {
    REPORTED,
    ASSIGNED,
    UNDER_REVIEW,
    INFO_REQUIRED,
    APPROVED,
    REJECTED,
    SETTLED;

    /** Statuses in which a claim still carries financial exposure. */
    public static final Set<ClaimStatus> OPEN = Collections.unmodifiableSet(
            EnumSet.of(REPORTED, ASSIGNED, UNDER_REVIEW, INFO_REQUIRED, APPROVED));

    public Set<ClaimStatus> allowedTransitions() {
        return switch (this) {
            case REPORTED -> EnumSet.of(ASSIGNED);
            case ASSIGNED -> EnumSet.of(UNDER_REVIEW);
            case UNDER_REVIEW -> EnumSet.of(APPROVED, REJECTED, INFO_REQUIRED);
            case INFO_REQUIRED -> EnumSet.of(UNDER_REVIEW);
            case APPROVED -> EnumSet.of(SETTLED);
            case REJECTED, SETTLED -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
