package com.insurer.claimflow.lifecycle.adapter.out.persistence;

import com.insurer.claimflow.lifecycle.application.port.in.ClaimSearchCriteria;
import com.insurer.claimflow.lifecycle.application.port.out.ClaimRepositoryPort;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.Claimant;
import com.insurer.claimflow.lifecycle.domain.Incident;
import com.insurer.claimflow.shared.domain.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
class ClaimPersistenceAdapter implements ClaimRepositoryPort {

    private final ClaimJpaRepository repository;

    ClaimPersistenceAdapter(ClaimJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Claim> findById(UUID id) {
        return repository.findById(id).map(ClaimPersistenceAdapter::toDomain);
    }

    @Override
    public void save(Claim claim) {
        Optional<ClaimJpaEntity> existing = repository.findById(claim.getId());
        if (existing.isPresent()) {
            ClaimJpaEntity entity = existing.get();
            // Guards against lost updates when the aggregate was loaded in an earlier transaction;
            // concurrent writers inside overlapping transactions are caught by @Version at flush time.
            if (!Objects.equals(entity.getVersion(), claim.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(ClaimJpaEntity.class, claim.getId());
            }
            applyState(entity, claim);
        } else {
            ClaimJpaEntity entity = new ClaimJpaEntity(claim.getId(), claim.getClaimNumber(), claim.getPolicyNumber(),
                    claim.getClaimant().name(), claim.getClaimant().email(), claim.getClaimant().phone(),
                    claim.getClaimedAmount(), claim.getCurrency(), claim.getCreatedAt());
            Incident incident = claim.getIncident();
            entity.attachIncident(new IncidentJpaEntity(incident.id(), entity, incident.type(), incident.date(),
                    incident.location(), incident.description(), claim.getCreatedAt()));
            applyState(entity, claim);
            repository.save(entity);
        }
    }

    @Override
    public PageResult<Claim> search(ClaimSearchCriteria criteria, int page, int size) {
        Specification<ClaimJpaEntity> spec = Specification.allOf(
                equalTo("status", criteria.status()),
                equalTo("assignedOfficerId", criteria.assignedOfficerId()),
                equalTo("policyNumber", criteria.policyNumber()));
        Page<ClaimJpaEntity> result = repository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))));
        return new PageResult<>(result.getContent().stream().map(ClaimPersistenceAdapter::toDomain).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static Specification<ClaimJpaEntity> equalTo(String attribute, Object value) {
        return value == null ? null : (root, query, cb) -> cb.equal(root.get(attribute), value);
    }

    private static void applyState(ClaimJpaEntity entity, Claim claim) {
        entity.applyMutableState(claim.getStatus(), claim.getAssignedOfficerId(), claim.getReserveAmount(),
                claim.getApprovedAmount(), claim.getSettledAmount(), claim.getPaymentReference(),
                claim.getDecisionReason(), claim.getUpdatedAt());
    }

    static Claim toDomain(ClaimJpaEntity e) {
        IncidentJpaEntity i = e.getIncident();
        return Claim.reconstitute(
                e.getId(), e.getClaimNumber(), e.getPolicyNumber(),
                new Claimant(e.getClaimantName(), e.getClaimantEmail(), e.getClaimantPhone()),
                new Incident(i.getId(), i.getType(), i.getIncidentDate(), i.getLocation(), i.getDescription()),
                e.getClaimedAmount(), e.getCurrency(), e.getStatus(), e.getAssignedOfficerId(),
                e.getReserveAmount(), e.getApprovedAmount(), e.getSettledAmount(), e.getPaymentReference(),
                e.getDecisionReason(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }
}
