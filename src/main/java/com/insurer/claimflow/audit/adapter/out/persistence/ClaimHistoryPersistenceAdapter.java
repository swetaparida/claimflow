package com.insurer.claimflow.audit.adapter.out.persistence;

import com.insurer.claimflow.audit.application.port.out.ClaimHistoryRepositoryPort;
import com.insurer.claimflow.audit.domain.ClaimHistoryEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class ClaimHistoryPersistenceAdapter implements ClaimHistoryRepositoryPort {

    private final ClaimHistoryJpaRepository repository;

    ClaimHistoryPersistenceAdapter(ClaimHistoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(ClaimHistoryEntry e) {
        repository.save(new ClaimHistoryJpaEntity(e.id(), e.eventId(), e.claimId(), e.eventType(), e.fromStatus(),
                e.toStatus(), e.actor(), e.details(), e.occurredAt()));
    }

    @Override
    public List<ClaimHistoryEntry> findByClaimId(UUID claimId) {
        return repository.findByClaimIdOrderBySeqAsc(claimId).stream()
                .map(e -> new ClaimHistoryEntry(e.getId(), e.getEventId(), e.getClaimId(), e.getEventType(),
                        e.getFromStatus(), e.getToStatus(), e.getActor(), e.getDetails(), e.getOccurredAt()))
                .toList();
    }
}
