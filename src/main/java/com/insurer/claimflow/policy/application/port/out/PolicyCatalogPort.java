package com.insurer.claimflow.policy.application.port.out;

import com.insurer.claimflow.policy.domain.Policy;

import java.util.Optional;

/**
 * Outbound port to the policy administration system.
 */
public interface PolicyCatalogPort {

    Optional<Policy> findByPolicyNumber(String policyNumber);
}
