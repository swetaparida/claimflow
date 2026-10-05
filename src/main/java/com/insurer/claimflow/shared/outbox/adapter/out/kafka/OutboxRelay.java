package com.insurer.claimflow.shared.outbox.adapter.out.kafka;

import com.insurer.claimflow.shared.messaging.Topics;
import com.insurer.claimflow.shared.outbox.adapter.out.persistence.OutboxEventJpaEntity;
import com.insurer.claimflow.shared.outbox.adapter.out.persistence.OutboxEventJpaRepository;
import com.insurer.claimflow.shared.outbox.domain.OutboxStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Kafka producer side of the transactional outbox: polls pending outbox rows and publishes them.
 * <p>
 * Delivery is at-least-once; consumers de-duplicate on the {@code eventId} header. Records are keyed
 * by aggregate id so all events for one claim land on the same partition in order. If a send fails the
 * batch stops, so later events are never published ahead of an earlier, failed one.
 */
@Component
@ConditionalOnProperty(prefix = "claimflow.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxEventJpaRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       TransactionTemplate transactionTemplate,
                       OutboxProperties properties,
                       Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${claimflow.outbox.poll-interval:500ms}")
    public void relayScheduled() {
        try {
            relayPendingEvents();
        } catch (RuntimeException e) {
            log.error("Outbox relay run failed", e);
        }
    }

    /**
     * @return number of events published in this run
     */
    public int relayPendingEvents() {
        Integer published = transactionTemplate.execute(status -> {
            List<OutboxEventJpaEntity> batch = repository.lockNextPendingBatch(properties.batchSize());
            int count = 0;
            for (OutboxEventJpaEntity event : batch) {
                try {
                    kafkaTemplate.send(toRecord(event)).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    event.markPublished(clock.instant());
                    count++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    event.markAttemptFailed("interrupted", properties.maxAttempts());
                    break;
                } catch (Exception e) {
                    event.markAttemptFailed(e.toString(), properties.maxAttempts());
                    log.warn("Failed to publish outbox event {} ({}) to {} - attempt {}/{}", event.getId(),
                            event.getEventType(), event.getTopic(), event.getAttempts(), properties.maxAttempts(), e);
                    break;
                }
            }
            return count;
        });
        int result = published == null ? 0 : published;
        if (result > 0) {
            log.debug("Relayed {} outbox event(s)", result);
        }
        return result;
    }

    @Scheduled(cron = "${claimflow.outbox.cleanup-cron:0 0 3 * * *}")
    public void purgePublished() {
        Integer removed = transactionTemplate.execute(status -> repository.deleteByStatusAndPublishedAtBefore(
                OutboxStatus.PUBLISHED, clock.instant().minus(properties.retention())));
        log.info("Purged {} published outbox event(s)", removed);
    }

    private ProducerRecord<String, String> toRecord(OutboxEventJpaEntity event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.getTopic(),
                event.getAggregateId().toString(), event.getPayload());
        record.headers()
                .add(Topics.HEADER_EVENT_ID, bytes(event.getId().toString()))
                .add(Topics.HEADER_EVENT_TYPE, bytes(event.getEventType()))
                .add(Topics.HEADER_AGGREGATE_ID, bytes(event.getAggregateId().toString()));
        return record;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
