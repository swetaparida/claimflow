package com.insurer.claimflow.workmanagement.adapter.out.persistence;

import com.insurer.claimflow.workmanagement.application.port.out.AssignmentRepositoryPort;
import com.insurer.claimflow.workmanagement.domain.Assignment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class AssignmentPersistenceAdapter implements AssignmentRepositoryPort {

    private final AssignmentJpaRepository repository;

    AssignmentPersistenceAdapter(AssignmentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Assignment> findActiveByClaimId(UUID claimId) {
        return repository.findByClaimIdAndActiveTrue(claimId).map(AssignmentPersistenceAdapter::toDomain);
    }

    @Override
    public List<Assignment> findByClaimId(UUID claimId) {
        return repository.findByClaimIdOrderByAssignedAtAsc(claimId).stream()
                .map(AssignmentPersistenceAdapter::toDomain).toList();
    }

    @Override
    public void save(Assignment a) {
        AssignmentJpaEntity entity = repository.findById(a.getId())
                .orElseGet(() -> new AssignmentJpaEntity(a.getId(), a.getClaimId(), a.getOfficerId(),
                        a.getAssignedBy(), a.getNote(), a.getAssignedAt()));
        entity.applyRelease(a.getReleasedAt());
        // Flush so the partial unique index (one active assignment per claim) sees the release
        // before the replacement row is inserted; Hibernate would otherwise order inserts first.
        repository.saveAndFlush(entity);
    }

    private static Assignment toDomain(AssignmentJpaEntity e) {
        return Assignment.reconstitute(e.getId(), e.getClaimId(), e.getOfficerId(), e.getAssignedBy(), e.getNote(),
                e.getAssignedAt(), e.getReleasedAt());
    }
}
