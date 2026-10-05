package com.insurer.claimflow.policy.adapter.out.catalog;

import com.insurer.claimflow.policy.application.port.out.PolicyCatalogPort;
import com.insurer.claimflow.policy.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class ConfiguredPolicyCatalogAdapter implements PolicyCatalogPort {

    private final Map<String, Policy> policies;

    ConfiguredPolicyCatalogAdapter(PolicyCatalogProperties properties) {
        this.policies = properties.catalog().stream()
                .map(e -> new Policy(e.policyNumber(), e.holderName(), e.productType(), e.status(),
                        e.effectiveFrom(), e.effectiveTo(), e.coverageLimit(), e.currency()))
                .collect(Collectors.toUnmodifiableMap(Policy::policyNumber, Function.identity()));
    }

    @Override
    public Optional<Policy> findByPolicyNumber(String policyNumber) {
        return Optional.ofNullable(policyNumber).map(policies::get);
    }
}
