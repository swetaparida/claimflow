package com.insurer.claimflow.policy.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only view of an insurance policy, owned by the external policy administration system.
 */
public record Policy(
        String policyNumber,
        String holderName,
        String productType,
        PolicyStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal coverageLimit,
        String currency) {

    public boolean isActive() {
        return status == PolicyStatus.ACTIVE;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(effectiveFrom) && !date.isAfter(effectiveTo);
    }
}
