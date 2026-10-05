package com.insurer.claimflow.policy.adapter.out.catalog;

import com.insurer.claimflow.policy.domain.PolicyStatus;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reference policy data. In production this adapter would be replaced by a client of the policy
 * administration system; the port stays the same.
 */
@ConfigurationProperties(prefix = "claimflow.policy")
public record PolicyCatalogProperties(List<Entry> catalog) {

    public PolicyCatalogProperties {
        catalog = catalog == null ? List.of() : List.copyOf(catalog);
    }

    public record Entry(
            String policyNumber,
            String holderName,
            String productType,
            PolicyStatus status,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal coverageLimit,
            String currency) {
    }
}
