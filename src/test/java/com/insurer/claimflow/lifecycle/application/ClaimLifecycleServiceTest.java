package com.insurer.claimflow.lifecycle.application;

import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.ApproveClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.RejectClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.SettleClaimCommand;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.ClaimFixtures;
import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import com.insurer.claimflow.shared.domain.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimLifecycleServiceTest {

    @Mock
    ClaimAggregateStore store;

    ClaimLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new ClaimLifecycleService(store, Clock.fixed(ClaimFixtures.NOW, ZoneOffset.UTC));
    }

    private Claim stub(Claim claim) {
        when(store.load(claim.getId())).thenReturn(claim);
        return claim;
    }

    @Test
    void approvesClaimUnderReview() {
        Claim claim = stub(ClaimFixtures.underReview());
        when(store.save(claim)).thenReturn(claim);

        Claim result = service.approve(new ApproveClaimCommand(claim.getId(), new BigDecimal("1200"), "ok", "mgr"));

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(result.getUpdatedAt()).isEqualTo(ClaimFixtures.NOW);
        verify(store).save(claim);
    }

    @Test
    void approvingUnreviewedClaimFailsWithoutSaving() {
        Claim claim = stub(ClaimFixtures.assigned());

        assertThatThrownBy(() -> service.approve(
                new ApproveClaimCommand(claim.getId(), new BigDecimal("100"), null, "mgr")))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(store, never()).save(any());
    }

    @Test
    void rejectsClaimUnderReview() {
        Claim claim = stub(ClaimFixtures.underReview());
        when(store.save(claim)).thenReturn(claim);

        Claim result = service.reject(new RejectClaimCommand(claim.getId(), "Excluded peril", "mgr"));

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(result.getDecisionReason()).isEqualTo("Excluded peril");
    }

    @Test
    void settlesApprovedClaim() {
        Claim claim = stub(ClaimFixtures.approved());
        when(store.save(claim)).thenReturn(claim);

        Claim result = service.settle(new SettleClaimCommand(claim.getId(), new BigDecimal("4000"), "PAY-1", "fin"));

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.SETTLED);
    }

    @Test
    void settlingUnapprovedClaimFails() {
        Claim claim = stub(ClaimFixtures.underReview());

        assertThatThrownBy(() -> service.settle(
                new SettleClaimCommand(claim.getId(), new BigDecimal("100"), "PAY-1", "fin")))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(store, never()).save(any());
    }
}
