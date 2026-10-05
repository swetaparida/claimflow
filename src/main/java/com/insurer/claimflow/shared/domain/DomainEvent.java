package com.insurer.claimflow.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Something meaningful that happened inside an aggregate.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    default String eventType() {
        return getClass().getSimpleName();
    }
}
