package com.insurer.claimflow.lifecycle.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReserveChanged(
        UUID eventId,
        UUID claimId,
        String claimNumber,
        BigDecimal previousReserve,
        BigDecimal newReserve,
        String currency,
        String reason,
        String actor,
        Instant occurredAt) implements ClaimDomainEvent {
}
