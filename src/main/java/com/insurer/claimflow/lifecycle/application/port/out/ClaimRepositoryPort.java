package com.insurer.claimflow.lifecycle.application.port.out;

import com.insurer.claimflow.lifecycle.application.port.in.ClaimSearchCriteria;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.shared.domain.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface ClaimRepositoryPort {

    Optional<Claim> findById(UUID id);

    void save(Claim claim);

    PageResult<Claim> search(ClaimSearchCriteria criteria, int page, int size);
}
