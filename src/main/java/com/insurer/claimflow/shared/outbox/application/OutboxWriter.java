package com.insurer.claimflow.shared.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurer.claimflow.shared.domain.DomainEvent;
import com.insurer.claimflow.shared.messaging.EventEnvelope;
import com.insurer.claimflow.shared.outbox.domain.OutboxMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serialises domain events into the integration-event envelope and stores them in the outbox.
 * <p>
 * Must run inside the business transaction ({@link Propagation#MANDATORY}) so that the state change
 * and the event are committed atomically – the core guarantee of the transactional outbox pattern.
 */
@Component
public class OutboxWriter {

    public static final int SCHEMA_VERSION = 1;

    private final OutboxStorePort store;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxStorePort store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, UUID aggregateId, String topic, DomainEvent event) {
        EventEnvelope envelope = new EventEnvelope(
                event.eventId(),
                event.eventType(),
                SCHEMA_VERSION,
                aggregateType,
                aggregateId,
                event.occurredAt(),
                objectMapper.valueToTree(event));
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            store.save(new OutboxMessage(event.eventId(), aggregateType, aggregateId, event.eventType(), topic,
                    payload, event.occurredAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialise event " + event.eventType(), e);
        }
    }
}
