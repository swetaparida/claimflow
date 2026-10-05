package com.insurer.claimflow.assessment.adapter.out.persistence;

import com.insurer.claimflow.assessment.domain.AssessmentOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "assessment")
public class AssessmentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "assessor_id", nullable = false, length = 64)
    private String assessorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private AssessmentOutcome outcome;

    @Column(name = "findings", nullable = false, length = 4000)
    private String findings;

    @Column(name = "recommended_reserve", precision = 19, scale = 2)
    private BigDecimal recommendedReserve;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    protected AssessmentJpaEntity() {
    }

    AssessmentJpaEntity(UUID id, UUID claimId, String assessorId, AssessmentOutcome outcome, String findings,
                        BigDecimal recommendedReserve, Instant assessedAt) {
        this.id = id;
        this.claimId = claimId;
        this.assessorId = assessorId;
        this.outcome = outcome;
        this.findings = findings;
        this.recommendedReserve = recommendedReserve;
        this.assessedAt = assessedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public String getAssessorId() {
        return assessorId;
    }

    public AssessmentOutcome getOutcome() {
        return outcome;
    }

    public String getFindings() {
        return findings;
    }

    public BigDecimal getRecommendedReserve() {
        return recommendedReserve;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }
}
