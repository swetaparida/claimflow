package com.insurer.claimflow.workmanagement.application;

import com.insurer.claimflow.lifecycle.application.ClaimAggregateStore;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.ClaimFixtures;
import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import com.insurer.claimflow.shared.domain.InvalidStateTransitionException;
import com.insurer.claimflow.workmanagement.application.port.in.AssignClaimUseCase.AssignClaimCommand;
import com.insurer.claimflow.workmanagement.application.port.out.AssignmentRepositoryPort;
import com.insurer.claimflow.workmanagement.domain.Assignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    ClaimAggregateStore claims;
    @Mock
    AssignmentRepositoryPort assignments;

    AssignmentService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentService(claims, assignments, Clock.fixed(ClaimFixtures.NOW, ZoneOffset.UTC));
    }

    @Test
    void firstAssignmentCreatesActiveAssignment() {
        Claim claim = ClaimFixtures.reported();
        when(claims.load(claim.getId())).thenReturn(claim);
        when(assignments.findActiveByClaimId(claim.getId())).thenReturn(Optional.empty());

        Assignment result = service.assign(new AssignClaimCommand(claim.getId(), "officer-1", "supervisor", "urgent"));

        assertThat(result.isActive()).isTrue();
        assertThat(result.getOfficerId()).isEqualTo("officer-1");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ASSIGNED);
        verify(assignments).save(result);
        verify(claims).save(claim);
    }

    @Test
    void reassignmentReleasesPreviousAssignmentFirst() {
        Claim claim = ClaimFixtures.assigned();
        Assignment previous = Assignment.create(claim.getId(), "officer-1", "supervisor", null,
                Instant.parse("2026-09-30T10:00:00Z"));
        when(claims.load(claim.getId())).thenReturn(claim);
        when(assignments.findActiveByClaimId(claim.getId())).thenReturn(Optional.of(previous));

        Assignment result = service.assign(new AssignClaimCommand(claim.getId(), "officer-2", "supervisor", null));

        assertThat(previous.isActive()).isFalse();
        assertThat(previous.getReleasedAt()).isEqualTo(ClaimFixtures.NOW);
        InOrder order = inOrder(assignments);
        order.verify(assignments).save(previous);
        order.verify(assignments).save(result);
        assertThat(claim.getAssignedOfficerId()).isEqualTo("officer-2");
    }

    @Test
    void settledClaimCannotBeAssigned() {
        Claim claim = ClaimFixtures.inStatus(ClaimStatus.SETTLED);
        when(claims.load(claim.getId())).thenReturn(claim);

        assertThatThrownBy(() -> service.assign(new AssignClaimCommand(claim.getId(), "officer-2", "sup", null)))
                .isInstanceOf(InvalidStateTransitionException.class);
        verify(assignments, never()).save(any());
    }
}
