package com.insurer.claimflow.assessment.application;

import com.insurer.claimflow.assessment.application.port.in.RecordAssessmentUseCase;
import com.insurer.claimflow.assessment.application.port.out.AssessmentRepositoryPort;
import com.insurer.claimflow.assessment.domain.Assessment;
import com.insurer.claimflow.assessment.domain.AssessmentOutcome;
import com.insurer.claimflow.lifecycle.application.ClaimAggregateStore;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records an assessment. The claim moves to UNDER_REVIEW (from ASSIGNED or INFO_REQUIRED), the reserve is updated
 * if the assessor recommends one, and the claim is parked in INFO_REQUIRED if more information is needed.
 */
@Service
public class AssessmentService implements RecordAssessmentUseCase {

    private final ClaimAggregateStore claims;
    private final AssessmentRepositoryPort assessments;
    private final Clock clock;

    public AssessmentService(ClaimAggregateStore claims, AssessmentRepositoryPort assessments, Clock clock) {
        this.claims = claims;
        this.assessments = assessments;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Assessment record(RecordAssessmentCommand cmd) {
        Instant now = clock.instant();
        Claim claim = claims.load(cmd.claimId());

        // Throws 409 if the claim has not been assigned yet or is already decided.
        claim.beginReview(cmd.assessorId(), now);

        if (!cmd.assessorId().equals(claim.getAssignedOfficerId())) {
            throw new BusinessRuleViolationException("ASSESSOR_NOT_ASSIGNED",
                    "Only the assigned officer (%s) may assess claim %s"
                            .formatted(claim.getAssignedOfficerId(), claim.getClaimNumber()));
        }
        if (cmd.recommendedReserve() != null) {
            claim.adjustReserve(cmd.recommendedReserve(), "ASSESSMENT", cmd.assessorId(), now);
        }
        if (cmd.outcome() == AssessmentOutcome.INFO_REQUIRED) {
            claim.requestInformation(cmd.findings(), cmd.assessorId(), now);
        }

        Assessment assessment = Assessment.record(claim.getId(), cmd.assessorId(), cmd.outcome(), cmd.findings(),
                cmd.recommendedReserve(), now);
        assessments.save(assessment);
        claims.save(claim);
        return assessment;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assessment> assessmentsOf(UUID claimId) {
        claims.load(claimId);
        return assessments.findByClaimId(claimId);
    }
}
