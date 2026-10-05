package com.insurer.claimflow.policy.application;

import com.insurer.claimflow.policy.application.port.out.PolicyCatalogPort;
import com.insurer.claimflow.policy.domain.Policy;
import com.insurer.claimflow.policy.domain.PolicyStatus;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyServiceTest {

    private static final Map<String, Policy> POLICIES = Map.of(
            "POL-1", new Policy("POL-1", "Jane", "MOTOR", PolicyStatus.ACTIVE, LocalDate.parse("2024-01-01"),
                    LocalDate.parse("2026-12-31"), new BigDecimal("10000"), "EUR"),
            "POL-2", new Policy("POL-2", "John", "HOME", PolicyStatus.LAPSED, LocalDate.parse("2024-01-01"),
                    LocalDate.parse("2026-12-31"), new BigDecimal("10000"), "EUR"));

    private final PolicyCatalogPort catalog = number -> Optional.ofNullable(POLICIES.get(number));
    private final PolicyService service = new PolicyService(catalog);

    @Test
    void acceptsCoveredClaim() {
        Policy policy = service.verifyCoverage("POL-1", LocalDate.parse("2025-05-01"), new BigDecimal("10000"), "eur");
        assertThat(policy.policyNumber()).isEqualTo("POL-1");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "POLICY_NOT_FOUND,                 POL-9, 2025-05-01, 100,      EUR",
            "POLICY_NOT_ACTIVE,                POL-2, 2025-05-01, 100,      EUR",
            "INCIDENT_OUTSIDE_COVERAGE_PERIOD, POL-1, 2023-12-31, 100,      EUR",
            "INCIDENT_OUTSIDE_COVERAGE_PERIOD, POL-1, 2027-01-01, 100,      EUR",
            "CURRENCY_MISMATCH,                POL-1, 2025-05-01, 100,      USD",
            "CLAIM_EXCEEDS_COVERAGE_LIMIT,     POL-1, 2025-05-01, 10000.01, EUR"
    })
    void rejectsUncoveredClaims(String expectedCode, String policy, LocalDate date, BigDecimal amount, String ccy) {
        assertThatThrownBy(() -> service.verifyCoverage(policy, date, amount, ccy))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo(expectedCode);
    }

    @Test
    void getPolicyThrowsNotFound() {
        assertThatThrownBy(() -> service.getPolicy("POL-404")).isInstanceOf(ResourceNotFoundException.class);
    }
}
