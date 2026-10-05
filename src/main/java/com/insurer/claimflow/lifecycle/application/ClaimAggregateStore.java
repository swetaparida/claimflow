package com.insurer.claimflow.lifecycle.application;

import com.insurer.claimflow.audit.application.port.in.AuditTrailUseCase;
import com.insurer.claimflow.audit.application.port.in.AuditTrailUseCase.RecordHistoryCommand;
import com.insurer.claimflow.lifecycle.application.port.out.ClaimRepositoryPort;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import com.insurer.claimflow.lifecycle.domain.event.ClaimAssigned;
import com.insurer.claimflow.lifecycle.domain.event.ClaimCreated;
import com.insurer.claimflow.lifecycle.domain.event.ClaimDomainEvent;
import com.insurer.claimflow.lifecycle.domain.event.ClaimStatusChanged;
import com.insurer.claimflow.lifecycle.domain.event.DecisionMade;
import com.insurer.claimflow.lifecycle.domain.event.ReserveChanged;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import com.insurer.claimflow.shared.messaging.Topics;
import com.insurer.claimflow.shared.outbox.application.OutboxWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single entry point other modules use to load and persist the {@link Claim} aggregate.
 * <p>
 * Saving drains the aggregate's domain events and, in the same database transaction, writes each one to the
 * outbox (for Kafka) and to the audit trail ({@code claim_history}). State, events and history therefore
 * commit or roll back together.
 */
@Component
public class ClaimAggregateStore {

    public static final String AGGREGATE_TYPE = "Claim";

    private final ClaimRepositoryPort repository;
    private final OutboxWriter outbox;
    private final AuditTrailUseCase auditTrail;

    public ClaimAggregateStore(ClaimRepositoryPort repository, OutboxWriter outbox, AuditTrailUseCase auditTrail) {
        this.repository = repository;
        this.outbox = outbox;
        this.auditTrail = auditTrail;
    }

    public Claim load(UUID claimId) {
        return repository.findById(claimId).orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Claim save(Claim claim) {
        repository.save(claim);
        for (ClaimDomainEvent event : claim.pullDomainEvents()) {
            outbox.append(AGGREGATE_TYPE, claim.getId(), topicFor(event), event);
            auditTrail.record(toHistory(event));
        }
        return claim;
    }

    static String topicFor(ClaimDomainEvent event) {
        return switch (event) {
            case ClaimCreated ignored -> Topics.CLAIM_CREATED;
            case ClaimAssigned ignored -> Topics.CLAIM_ASSIGNED;
            case ClaimStatusChanged ignored -> Topics.STATUS_CHANGED;
            case ReserveChanged ignored -> Topics.RESERVE_CHANGED;
            case DecisionMade ignored -> Topics.DECISION_MADE;
        };
    }

    private static RecordHistoryCommand toHistory(ClaimDomainEvent event) {
        String from = null;
        String to = null;
        if (event instanceof ClaimStatusChanged changed) {
            from = changed.fromStatus().name();
            to = changed.toStatus().name();
        } else if (event instanceof ClaimCreated) {
            to = ClaimStatus.REPORTED.name();
        }
        return new RecordHistoryCommand(event.eventId(), event.claimId(), event.eventType(), from, to,
                event.actor(), event, event.occurredAt());
    }
}
