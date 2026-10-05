package com.insurer.claimflow.lifecycle.domain;

import com.insurer.claimflow.lifecycle.domain.event.ClaimAssigned;
import com.insurer.claimflow.lifecycle.domain.event.ClaimCreated;
import com.insurer.claimflow.lifecycle.domain.event.ClaimDomainEvent;
import com.insurer.claimflow.lifecycle.domain.event.ClaimStatusChanged;
import com.insurer.claimflow.lifecycle.domain.event.DecisionMade;
import com.insurer.claimflow.lifecycle.domain.event.ReserveChanged;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import com.insurer.claimflow.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static com.insurer.claimflow.lifecycle.domain.ClaimFixtures.NOW;
import static com.insurer.claimflow.lifecycle.domain.ClaimFixtures.OFFICER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimTest {

    @Nested
    class Submission {

        @Test
        void newClaimIsReportedWithInitialReserveAndEvents() {
            Claim claim = Claim.submit(java.util.UUID.randomUUID(), "CLM-1", "POL-1001",
                    new Claimant("Jane", "jane@example.com", null),
                    new Incident(java.util.UUID.randomUUID(), IncidentType.FIRE, java.time.LocalDate.parse("2026-09-01"),
                            null, "Kitchen fire"),
                    new BigDecimal("1000"), "eur", "claimant", NOW);

            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REPORTED);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("1000.00");
            assertThat(claim.getCurrency()).isEqualTo("EUR");
            List<ClaimDomainEvent> events = claim.pullDomainEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(ClaimCreated.class);
            assertThat(events.get(1)).isInstanceOfSatisfying(ReserveChanged.class, e -> {
                assertThat(e.previousReserve()).isEqualByComparingTo("0");
                assertThat(e.newReserve()).isEqualByComparingTo("1000");
                assertThat(e.reason()).isEqualTo("INITIAL_RESERVE");
            });
            assertThat(claim.pullDomainEvents()).isEmpty();
        }

        @Test
        void claimedAmountMustBePositive() {
            assertThatThrownBy(() -> Claim.submit(java.util.UUID.randomUUID(), "CLM-1", "POL-1001",
                    new Claimant("Jane", "jane@example.com", null),
                    new Incident(java.util.UUID.randomUUID(), IncidentType.FIRE, java.time.LocalDate.parse("2026-09-01"),
                            null, "Kitchen fire"),
                    BigDecimal.ZERO, "EUR", "claimant", NOW))
                    .isInstanceOf(BusinessRuleViolationException.class);
        }
    }

    @Nested
    class Assignment {

        @Test
        void firstAssignmentMovesReportedToAssigned() {
            Claim claim = ClaimFixtures.reported();

            claim.assignTo(OFFICER, "supervisor", NOW);

            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ASSIGNED);
            assertThat(claim.getAssignedOfficerId()).isEqualTo(OFFICER);
            List<ClaimDomainEvent> events = claim.pullDomainEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(ClaimAssigned.class);
            assertThat(events.get(1)).isInstanceOfSatisfying(ClaimStatusChanged.class, e -> {
                assertThat(e.fromStatus()).isEqualTo(ClaimStatus.REPORTED);
                assertThat(e.toStatus()).isEqualTo(ClaimStatus.ASSIGNED);
            });
        }

        @Test
        void reassignmentKeepsStatus() {
            Claim claim = ClaimFixtures.underReview();

            claim.assignTo("officer-2", "supervisor", NOW);

            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
            assertThat(claim.getAssignedOfficerId()).isEqualTo("officer-2");
            assertThat(claim.pullDomainEvents()).singleElement().isInstanceOfSatisfying(ClaimAssigned.class,
                    e -> assertThat(e.previousOfficerId()).isEqualTo(OFFICER));
        }

        @Test
        void assigningToSameOfficerIsRejected() {
            Claim claim = ClaimFixtures.assigned();
            assertThatThrownBy(() -> claim.assignTo(OFFICER, "supervisor", NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .hasMessageContaining("already assigned");
        }

        @ParameterizedTest
        @EnumSource(value = ClaimStatus.class, names = {"APPROVED", "REJECTED", "SETTLED"})
        void decidedClaimsCannotBeAssigned(ClaimStatus status) {
            Claim claim = ClaimFixtures.inStatus(status);
            assertThatThrownBy(() -> claim.assignTo("officer-9", "supervisor", NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    class Review {

        @Test
        void claimMustBeAssignedBeforeReview() {
            Claim claim = ClaimFixtures.reported();
            assertThatThrownBy(() -> claim.beginReview(OFFICER, NOW))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("REPORTED to UNDER_REVIEW");
        }

        @Test
        void beginReviewIsIdempotentWhenAlreadyUnderReview() {
            Claim claim = ClaimFixtures.underReview();
            claim.beginReview(OFFICER, NOW);
            assertThat(claim.pullDomainEvents()).isEmpty();
        }

        @Test
        void infoRequiredReturnsToUnderReview() {
            Claim claim = ClaimFixtures.inStatus(ClaimStatus.INFO_REQUIRED);
            claim.beginReview(OFFICER, NOW);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        }

        @Test
        void reserveCanBeAdjustedWhileOpen() {
            Claim claim = ClaimFixtures.underReview();
            claim.adjustReserve(new BigDecimal("3000"), "ASSESSMENT", OFFICER, NOW);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("3000");
            assertThat(claim.pullDomainEvents()).singleElement().isInstanceOf(ReserveChanged.class);
        }

        @Test
        void unchangedReserveEmitsNoEvent() {
            Claim claim = ClaimFixtures.underReview();
            claim.adjustReserve(claim.getReserveAmount(), "ASSESSMENT", OFFICER, NOW);
            assertThat(claim.pullDomainEvents()).isEmpty();
        }
    }

    @Nested
    class Decisions {

        @ParameterizedTest
        @EnumSource(value = ClaimStatus.class, names = "UNDER_REVIEW", mode = EnumSource.Mode.EXCLUDE)
        void approvalRequiresReview(ClaimStatus status) {
            Claim claim = ClaimFixtures.inStatus(status);
            assertThatThrownBy(() -> claim.approve(new BigDecimal("100"), null, "manager", NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @ParameterizedTest
        @EnumSource(value = ClaimStatus.class, names = "UNDER_REVIEW", mode = EnumSource.Mode.EXCLUDE)
        void rejectionRequiresReview(ClaimStatus status) {
            Claim claim = ClaimFixtures.inStatus(status);
            assertThatThrownBy(() -> claim.reject("Fraud", "manager", NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void approveSetsAmountReserveAndEmitsDecision() {
            Claim claim = ClaimFixtures.underReview();

            claim.approve(new BigDecimal("4200.00"), "Within cover", "manager", NOW);

            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
            assertThat(claim.getApprovedAmount()).isEqualByComparingTo("4200");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("4200");
            assertThat(claim.pullDomainEvents()).extracting(e -> e.getClass().getSimpleName())
                    .containsExactly("ClaimStatusChanged", "DecisionMade", "ReserveChanged");
        }

        @Test
        void approvedAmountCannotExceedClaimedAmount() {
            Claim claim = ClaimFixtures.underReview();
            assertThatThrownBy(() -> claim.approve(new BigDecimal("4500.01"), null, "manager", NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("code").isEqualTo("APPROVED_AMOUNT_EXCEEDS_CLAIM");
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        }

        @Test
        void rejectReleasesReserve() {
            Claim claim = ClaimFixtures.underReview();

            claim.reject("Policy exclusion 4.2", "manager", NOW);

            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
            assertThat(claim.pullDomainEvents()).anySatisfy(e -> assertThat(e)
                    .isInstanceOfSatisfying(DecisionMade.class, d -> assertThat(d.decision()).isEqualTo(Decision.REJECTED)));
        }
    }

    @Nested
    class Settlement {

        @ParameterizedTest
        @EnumSource(value = ClaimStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
        void settlementRequiresApproval(ClaimStatus status) {
            Claim claim = ClaimFixtures.inStatus(status);
            assertThatThrownBy(() -> claim.settle(new BigDecimal("100"), "PAY-1", "finance", NOW))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        void settleRecordsPaymentAndClearsReserve() {
            Claim claim = ClaimFixtures.approved();

            claim.settle(new BigDecimal("4000.00"), "PAY-42", "finance", NOW);

            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
            assertThat(claim.getSettledAmount()).isEqualByComparingTo("4000");
            assertThat(claim.getPaymentReference()).isEqualTo("PAY-42");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
        }

        @Test
        void settlementCannotExceedApprovedAmount() {
            Claim claim = ClaimFixtures.approved();
            assertThatThrownBy(() -> claim.settle(new BigDecimal("4000.01"), "PAY-1", "finance", NOW))
                    .isInstanceOf(BusinessRuleViolationException.class)
                    .extracting("code").isEqualTo("SETTLEMENT_EXCEEDS_APPROVED_AMOUNT");
        }
    }
}
