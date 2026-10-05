package com.insurer.claimflow.lifecycle.domain.event;

import com.insurer.claimflow.lifecycle.domain.Decision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DecisionMade(
        UUID eventId,
        UUID claimId,
        String claimNumber,
        Decision decision,
        BigDecimal approvedAmount,
        String currency,
        String reason,
        String actor,
        Instant occurredAt) implements ClaimDomainEvent {
}
