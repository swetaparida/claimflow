package com.insurer.claimflow.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A claims officer's evaluation of a claim. Immutable once recorded.
 */
public record Assessment(
        UUID id,
        UUID claimId,
        String assessorId,
        AssessmentOutcome outcome,
        String findings,
        BigDecimal recommendedReserve,
        Instant assessedAt) {

    public Assessment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(claimId);
        Objects.requireNonNull(assessorId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(findings);
        Objects.requireNonNull(assessedAt);
    }

    public static Assessment record(UUID claimId, String assessorId, AssessmentOutcome outcome, String findings,
                                    BigDecimal recommendedReserve, Instant now) {
        return new Assessment(UUID.randomUUID(), claimId, assessorId, outcome, findings, recommendedReserve, now);
    }
}
