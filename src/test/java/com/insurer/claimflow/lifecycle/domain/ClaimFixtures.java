package com.insurer.claimflow.lifecycle.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Builders for claims in a given lifecycle state, for unit tests.
 */
public final class ClaimFixtures {

    public static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    public static final String OFFICER = "officer-1";

    private ClaimFixtures() {
    }

    public static Claim reported() {
        Claim claim = Claim.submit(UUID.randomUUID(), "CLM-2026-00000001", "POL-1001",
                new Claimant("Jane Doe", "jane@example.com", null),
                new Incident(UUID.randomUUID(), IncidentType.AUTO_COLLISION, LocalDate.parse("2026-09-28"),
                        "Berlin", "Rear-ended at a traffic light"),
                new BigDecimal("4500.00"), "EUR", "claimant", NOW);
        claim.pullDomainEvents();
        return claim;
    }

    public static Claim assigned() {
        Claim claim = reported();
        claim.assignTo(OFFICER, "supervisor", NOW);
        claim.pullDomainEvents();
        return claim;
    }

    public static Claim underReview() {
        Claim claim = assigned();
        claim.beginReview(OFFICER, NOW);
        claim.pullDomainEvents();
        return claim;
    }

    public static Claim approved() {
        Claim claim = underReview();
        claim.approve(new BigDecimal("4000.00"), "Covered", "manager", NOW);
        claim.pullDomainEvents();
        return claim;
    }

    public static Claim inStatus(ClaimStatus status) {
        return switch (status) {
            case REPORTED -> reported();
            case ASSIGNED -> assigned();
            case UNDER_REVIEW -> underReview();
            case APPROVED -> approved();
            case INFO_REQUIRED -> {
                Claim c = underReview();
                c.requestInformation("Need photos", OFFICER, NOW);
                c.pullDomainEvents();
                yield c;
            }
            case REJECTED -> {
                Claim c = underReview();
                c.reject("Not covered", "manager", NOW);
                c.pullDomainEvents();
                yield c;
            }
            case SETTLED -> {
                Claim c = approved();
                c.settle(new BigDecimal("4000.00"), "PAY-1", "finance", NOW);
                c.pullDomainEvents();
                yield c;
            }
        };
    }
}
