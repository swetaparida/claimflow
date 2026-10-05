package com.insurer.claimflow.lifecycle.application.port.in;

import com.insurer.claimflow.lifecycle.domain.ClaimStatus;

/**
 * Optional filters for claim searches; {@code null} means "any".
 */
public record ClaimSearchCriteria(ClaimStatus status, String assignedOfficerId, String policyNumber) {

    public static ClaimSearchCriteria any() {
        return new ClaimSearchCriteria(null, null, null);
    }
}
