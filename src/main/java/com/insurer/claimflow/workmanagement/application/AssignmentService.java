package com.insurer.claimflow.workmanagement.application;

import com.insurer.claimflow.lifecycle.application.ClaimAggregateStore;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.workmanagement.application.port.in.AssignClaimUseCase;
import com.insurer.claimflow.workmanagement.application.port.out.AssignmentRepositoryPort;
import com.insurer.claimflow.workmanagement.domain.Assignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AssignmentService implements AssignClaimUseCase {

    private final ClaimAggregateStore claims;
    private final AssignmentRepositoryPort assignments;
    private final Clock clock;

    public AssignmentService(ClaimAggregateStore claims, AssignmentRepositoryPort assignments, Clock clock) {
        this.claims = claims;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Assignment assign(AssignClaimCommand cmd) {
        Instant now = clock.instant();
        Claim claim = claims.load(cmd.claimId());
        // Lifecycle rules are checked by the aggregate before any work-management state changes.
        claim.assignTo(cmd.officerId(), cmd.assignedBy(), now);

        assignments.findActiveByClaimId(cmd.claimId()).ifPresent(previous -> {
            previous.release(now);
            assignments.save(previous);
        });
        Assignment assignment = Assignment.create(cmd.claimId(), cmd.officerId(), cmd.assignedBy(), cmd.note(), now);
        assignments.save(assignment);
        claims.save(claim);
        return assignment;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assignment> assignmentsOf(UUID claimId) {
        claims.load(claimId);
        return assignments.findByClaimId(claimId);
    }
}
