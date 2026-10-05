package com.insurer.claimflow.policy.application;

import com.insurer.claimflow.policy.application.port.in.PolicyQueryUseCase;
import com.insurer.claimflow.policy.application.port.out.PolicyCatalogPort;
import com.insurer.claimflow.policy.domain.Policy;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class PolicyService implements PolicyQueryUseCase {

    private final PolicyCatalogPort catalog;

    public PolicyService(PolicyCatalogPort catalog) {
        this.catalog = catalog;
    }

    @Override
    public Policy getPolicy(String policyNumber) {
        return catalog.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Policy", policyNumber));
    }

    @Override
    public Policy verifyCoverage(String policyNumber, LocalDate incidentDate, BigDecimal claimedAmount, String currency) {
        Policy policy = catalog.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new BusinessRuleViolationException("POLICY_NOT_FOUND",
                        "Policy %s does not exist".formatted(policyNumber)));
        if (!policy.isActive()) {
            throw new BusinessRuleViolationException("POLICY_NOT_ACTIVE",
                    "Policy %s is %s".formatted(policyNumber, policy.status()));
        }
        if (!policy.covers(incidentDate)) {
            throw new BusinessRuleViolationException("INCIDENT_OUTSIDE_COVERAGE_PERIOD",
                    "Incident date %s is outside the coverage period %s to %s"
                            .formatted(incidentDate, policy.effectiveFrom(), policy.effectiveTo()));
        }
        if (!policy.currency().equalsIgnoreCase(currency)) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Claim currency %s does not match policy currency %s".formatted(currency, policy.currency()));
        }
        if (claimedAmount.compareTo(policy.coverageLimit()) > 0) {
            throw new BusinessRuleViolationException("CLAIM_EXCEEDS_COVERAGE_LIMIT",
                    "Claimed amount %s exceeds coverage limit %s".formatted(claimedAmount, policy.coverageLimit()));
        }
        return policy;
    }
}
