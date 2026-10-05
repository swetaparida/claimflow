package com.insurer.claimflow.shared.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire format of every integration event published to Kafka.
 *
 * @param eventId       globally unique id, used by consumers for idempotency
 * @param eventType     logical event name, e.g. {@code ClaimCreated}
 * @param schemaVersion payload schema version
 * @param aggregateType aggregate that emitted the event, e.g. {@code Claim}
 * @param aggregateId   aggregate identifier, also used as the Kafka record key
 * @param occurredAt    business time of the event
 * @param data          event-specific payload
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        JsonNode data) {
}
