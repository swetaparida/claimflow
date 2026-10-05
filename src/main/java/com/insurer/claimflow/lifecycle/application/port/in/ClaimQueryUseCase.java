package com.insurer.claimflow.lifecycle.application.port.in;

import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.shared.domain.PageResult;

import java.util.UUID;

public interface ClaimQueryUseCase {

    Claim getClaim(UUID claimId);

    PageResult<Claim> search(ClaimSearchCriteria criteria, int page, int size);
}
