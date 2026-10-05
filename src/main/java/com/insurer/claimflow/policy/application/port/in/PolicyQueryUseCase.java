package com.insurer.claimflow.policy.application.port.in;

import com.insurer.claimflow.policy.domain.Policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Public API of the Policy Reference module used by other modules.
 */
public interface PolicyQueryUseCase {

    Policy getPolicy(String policyNumber);

    /**
     * Verifies the policy exists, is active, covers the incident date and that the claimed amount
     * and currency fit the coverage.
     *
     * @throws com.insurer.claimflow.shared.domain.BusinessRuleViolationException if any rule fails
     */
    Policy verifyCoverage(String policyNumber, LocalDate incidentDate, BigDecimal claimedAmount, String currency);
}
