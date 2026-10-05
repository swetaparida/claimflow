package com.insurer.claimflow.assessment.application;

import com.insurer.claimflow.assessment.application.port.in.RecordAssessmentUseCase.RecordAssessmentCommand;
import com.insurer.claimflow.assessment.application.port.out.AssessmentRepositoryPort;
import com.insurer.claimflow.assessment.domain.Assessment;
import com.insurer.claimflow.assessment.domain.AssessmentOutcome;
import com.insurer.claimflow.lifecycle.application.ClaimAggregateStore;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.ClaimFixtures;
import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import com.insurer.claimflow.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import static com.insurer.claimflow.lifecycle.domain.ClaimFixtures.OFFICER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    ClaimAggregateStore claims;
    @Mock
    AssessmentRepositoryPort assessments;

    AssessmentService service;

    @BeforeEach
    void setUp() {
        service = new AssessmentService(claims, assessments, Clock.fixed(ClaimFixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void assessmentMovesAssignedClaimUnderReviewAndAdjustsReserve() {
        Claim claim = ClaimFixtures.assigned();
        when(claims.load(claim.getId())).thenReturn(claim);

        Assessment result = service.record(new RecordAssessmentCommand(claim.getId(), OFFICER,
                AssessmentOutcome.RECOMMEND_APPROVAL, "Damage verified", new BigDecimal("3800")));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(claim.getReserveAmount()).isEqualByComparingTo("3800");
        assertThat(result.claimId()).isEqualTo(claim.getId());
        verify(assessments).save(result);
        verify(claims).save(claim);
    }

    @Test
    void infoRequiredOutcomeParksClaim() {
        Claim claim = ClaimFixtures.assigned();
        when(claims.load(claim.getId())).thenReturn(claim);

        service.record(new RecordAssessmentCommand(claim.getId(), OFFICER, AssessmentOutcome.INFO_REQUIRED,
                "Police report missing", null));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INFO_REQUIRED);
    }

    @Test
    void unassignedClaimCannotBeAssessed() {
        Claim claim = ClaimFixtures.reported();
        when(claims.load(claim.getId())).thenReturn(claim);

        assertThatThrownBy(() -> service.record(new RecordAssessmentCommand(claim.getId(), OFFICER,
                AssessmentOutcome.RECOMMEND_APPROVAL, "ok", null)))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(assessments, never()).save(any());
        verify(claims, never()).save(any());
    }

    @Test
    void onlyAssignedOfficerMayAssess() {
        Claim claim = ClaimFixtures.assigned();
        when(claims.load(claim.getId())).thenReturn(claim);

        assertThatThrownBy(() -> service.record(new RecordAssessmentCommand(claim.getId(), "someone-else",
                AssessmentOutcome.RECOMMEND_APPROVAL, "ok", null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("code").isEqualTo("ASSESSOR_NOT_ASSIGNED");
        verify(claims, never()).save(any());
    }
}
