package com.insurer.claimflow.lifecycle.domain.event;

import com.insurer.claimflow.lifecycle.domain.ClaimStatus;

import java.time.Instant;
import java.util.UUID;

public record ClaimStatusChanged(
        UUID eventId,
        UUID claimId,
        String claimNumber,
        ClaimStatus fromStatus,
        ClaimStatus toStatus,
        String reason,
        String actor,
        Instant occurredAt) implements ClaimDomainEvent {
}
