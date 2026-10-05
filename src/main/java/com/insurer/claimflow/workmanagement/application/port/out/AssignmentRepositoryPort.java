package com.insurer.claimflow.workmanagement.application.port.out;

import com.insurer.claimflow.workmanagement.domain.Assignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepositoryPort {

    Optional<Assignment> findActiveByClaimId(UUID claimId);

    List<Assignment> findByClaimId(UUID claimId);

    /** Persists and flushes immediately so a release is written before the replacing assignment. */
    void save(Assignment assignment);
}
