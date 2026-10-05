package com.insurer.claimflow.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurer.claimflow.audit.application.port.in.AuditTrailUseCase;
import com.insurer.claimflow.audit.application.port.out.ClaimHistoryRepositoryPort;
import com.insurer.claimflow.audit.domain.ClaimHistoryEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditTrailService implements AuditTrailUseCase {

    private final ClaimHistoryRepositoryPort repository;
    private final ObjectMapper objectMapper;

    public AuditTrailService(ClaimHistoryRepositoryPort repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(RecordHistoryCommand c) {
        repository.append(new ClaimHistoryEntry(UUID.randomUUID(), c.eventId(), c.claimId(), c.eventType(),
                c.fromStatus(), c.toStatus(), c.actor(), toJson(c.details()), c.occurredAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClaimHistoryEntry> historyOf(UUID claimId) {
        return repository.findByClaimId(claimId);
    }

    private String toJson(Object details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialise audit details", e);
        }
    }
}
