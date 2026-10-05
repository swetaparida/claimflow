package com.insurer.claimflow.workmanagement.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AssignmentJpaRepository extends JpaRepository<AssignmentJpaEntity, UUID> {

    Optional<AssignmentJpaEntity> findByClaimIdAndActiveTrue(UUID claimId);

    List<AssignmentJpaEntity> findByClaimIdOrderByAssignedAtAsc(UUID claimId);
}
