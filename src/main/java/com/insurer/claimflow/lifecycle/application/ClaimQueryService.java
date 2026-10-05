package com.insurer.claimflow.lifecycle.application;

import com.insurer.claimflow.lifecycle.application.port.in.ClaimQueryUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimSearchCriteria;
import com.insurer.claimflow.lifecycle.application.port.out.ClaimRepositoryPort;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.shared.domain.PageResult;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ClaimQueryService implements ClaimQueryUseCase {

    static final int MAX_PAGE_SIZE = 100;

    private final ClaimRepositoryPort repository;

    public ClaimQueryService(ClaimRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Claim getClaim(UUID claimId) {
        return repository.findById(claimId).orElseThrow(() -> new ResourceNotFoundException("Claim", claimId));
    }

    @Override
    public PageResult<Claim> search(ClaimSearchCriteria criteria, int page, int size) {
        return repository.search(criteria == null ? ClaimSearchCriteria.any() : criteria,
                Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}
