package com.insurer.claimflow.notification.adapter.in.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurer.claimflow.notification.application.port.in.HandleClaimEventUseCase;
import com.insurer.claimflow.notification.application.port.in.HandleClaimEventUseCase.ClaimEventNotice;
import com.insurer.claimflow.shared.messaging.EventEnvelope;
import com.insurer.claimflow.shared.messaging.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka consumer for claim events relevant to notifications.
 */
@Component
class ClaimEventsKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventsKafkaConsumer.class);

    private final HandleClaimEventUseCase handler;
    private final ObjectMapper objectMapper;

    ClaimEventsKafkaConsumer(HandleClaimEventUseCase handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            id = "notification-consumer",
            groupId = "${claimflow.notification.group-id:claimflow-notification}",
            topics = {Topics.CLAIM_CREATED, Topics.CLAIM_ASSIGNED, Topics.STATUS_CHANGED, Topics.DECISION_MADE},
            autoStartup = "${claimflow.notification.enabled:true}")
    void onClaimEvent(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = parse(record);
        log.debug("Received {} {} for claim {} from {}-{}@{}", envelope.eventType(), envelope.eventId(),
                envelope.aggregateId(), record.topic(), record.partition(), record.offset());
        handler.handle(new ClaimEventNotice(envelope.eventId(), envelope.eventType(), envelope.aggregateId(),
                flatten(envelope.data())));
    }

    private EventEnvelope parse(ConsumerRecord<String, String> record) {
        try {
            EventEnvelope envelope = objectMapper.readValue(record.value(), EventEnvelope.class);
            if (envelope.eventId() == null || envelope.eventType() == null) {
                throw new IllegalArgumentException("Envelope missing eventId/eventType");
            }
            return envelope;
        } catch (JsonProcessingException e) {
            // Not retryable: routed straight to the dead-letter topic by the error handler.
            throw new IllegalArgumentException("Malformed event on " + record.topic() + " offset " + record.offset(), e);
        }
    }

    private static Map<String, String> flatten(JsonNode data) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (data != null) {
            data.fields().forEachRemaining(f -> {
                if (!f.getValue().isNull()) {
                    attributes.put(f.getKey(), f.getValue().asText());
                }
            });
        }
        return attributes;
    }
}
