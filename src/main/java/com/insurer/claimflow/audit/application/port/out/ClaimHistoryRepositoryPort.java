package com.insurer.claimflow.audit.application.port.out;

import com.insurer.claimflow.audit.domain.ClaimHistoryEntry;

import java.util.List;
import java.util.UUID;

public interface ClaimHistoryRepositoryPort {

    void append(ClaimHistoryEntry entry);

    List<ClaimHistoryEntry> findByClaimId(UUID claimId);
}
