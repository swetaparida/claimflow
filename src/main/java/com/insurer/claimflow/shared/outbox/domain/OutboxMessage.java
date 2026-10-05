package com.insurer.claimflow.shared.outbox.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * An integration event waiting to be relayed to the message broker.
 * The id equals the originating domain event id so downstream consumers can de-duplicate.
 */
public record OutboxMessage(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String topic,
        String payload,
        Instant createdAt) {
}
