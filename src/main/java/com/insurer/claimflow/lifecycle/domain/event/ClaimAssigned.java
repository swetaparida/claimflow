package com.insurer.claimflow.lifecycle.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ClaimAssigned(
        UUID eventId,
        UUID claimId,
        String claimNumber,
        String officerId,
        String previousOfficerId,
        String actor,
        Instant occurredAt) implements ClaimDomainEvent {
}
