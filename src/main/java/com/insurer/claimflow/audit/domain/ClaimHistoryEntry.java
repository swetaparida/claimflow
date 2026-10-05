package com.insurer.claimflow.audit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record of something that happened to a claim.
 */
public record ClaimHistoryEntry(
        UUID id,
        UUID eventId,
        UUID claimId,
        String eventType,
        String fromStatus,
        String toStatus,
        String actor,
        String details,
        Instant occurredAt) {
}
