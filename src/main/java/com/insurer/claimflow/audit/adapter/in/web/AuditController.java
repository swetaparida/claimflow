package com.insurer.claimflow.audit.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurer.claimflow.audit.application.port.in.AuditTrailUseCase;
import com.insurer.claimflow.audit.domain.ClaimHistoryEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Audit")
class AuditController {

    record HistoryEntryResponse(UUID eventId, String eventType, String fromStatus, String toStatus, String actor,
                                JsonNode details, Instant occurredAt) {
    }

    private final AuditTrailUseCase auditTrail;
    private final ObjectMapper objectMapper;

    AuditController(AuditTrailUseCase auditTrail, ObjectMapper objectMapper) {
        this.auditTrail = auditTrail;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/v1/claims/{id}/history")
    @Operation(summary = "Chronological audit trail of a claim")
    List<HistoryEntryResponse> history(@PathVariable UUID id) {
        return auditTrail.historyOf(id).stream().map(this::toResponse).toList();
    }

    private HistoryEntryResponse toResponse(ClaimHistoryEntry e) {
        try {
            JsonNode details = e.details() == null ? null : objectMapper.readTree(e.details());
            return new HistoryEntryResponse(e.eventId(), e.eventType(), e.fromStatus(), e.toStatus(), e.actor(),
                    details, e.occurredAt());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
