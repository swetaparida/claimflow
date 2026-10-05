package com.insurer.claimflow.shared.outbox;

import com.insurer.claimflow.shared.messaging.Topics;
import com.insurer.claimflow.shared.outbox.adapter.out.kafka.OutboxProperties;
import com.insurer.claimflow.shared.outbox.adapter.out.kafka.OutboxRelay;
import com.insurer.claimflow.shared.outbox.adapter.out.persistence.OutboxEventJpaEntity;
import com.insurer.claimflow.shared.outbox.adapter.out.persistence.OutboxEventJpaRepository;
import com.insurer.claimflow.shared.outbox.domain.OutboxStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Mock
    OutboxEventJpaRepository repository;
    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    OutboxRelay relay;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        OutboxProperties properties = new OutboxProperties(true, Duration.ofMillis(500), 50, 3,
                Duration.ofSeconds(1), Duration.ofDays(7));
        relay = new OutboxRelay(repository, kafkaTemplate, new TransactionTemplate(txManager), properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OutboxEventJpaEntity event(String topic) {
        return new OutboxEventJpaEntity(UUID.randomUUID(), "Claim", UUID.randomUUID(), "ClaimCreated", topic,
                "{\"eventType\":\"ClaimCreated\"}", NOW);
    }

    @Test
    void publishesPendingEventsWithKeyAndHeaders() {
        OutboxEventJpaEntity e1 = event(Topics.CLAIM_CREATED);
        OutboxEventJpaEntity e2 = event(Topics.RESERVE_CHANGED);
        when(repository.lockNextPendingBatch(50)).thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        int published = relay.relayPendingEvents();

        assertThat(published).isEqualTo(2);
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(e1.getPublishedAt()).isEqualTo(NOW);
        ArgumentCaptor<ProducerRecord<String, String>> records = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(2)).send(records.capture());
        ProducerRecord<String, String> first = records.getAllValues().get(0);
        assertThat(first.topic()).isEqualTo(Topics.CLAIM_CREATED);
        assertThat(first.key()).isEqualTo(e1.getAggregateId().toString());
        assertThat(new String(first.headers().lastHeader(Topics.HEADER_EVENT_ID).value(), StandardCharsets.UTF_8))
                .isEqualTo(e1.getId().toString());
    }

    @Test
    void stopsBatchOnFailureToPreserveOrdering() {
        OutboxEventJpaEntity e1 = event(Topics.CLAIM_CREATED);
        OutboxEventJpaEntity e2 = event(Topics.RESERVE_CHANGED);
        when(repository.lockNextPendingBatch(50)).thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        int published = relay.relayPendingEvents();

        assertThat(published).isZero();
        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(e1.getAttempts()).isEqualTo(1);
        assertThat(e1.getLastError()).contains("broker down");
        assertThat(e2.getAttempts()).isZero();
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void marksEventFailedAfterMaxAttempts() {
        OutboxEventJpaEntity e1 = event(Topics.CLAIM_CREATED);
        when(repository.lockNextPendingBatch(50)).thenReturn(List.of(e1));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        for (int i = 0; i < 3; i++) {
            relay.relayPendingEvents();
        }

        assertThat(e1.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(e1.getAttempts()).isEqualTo(3);
    }
}
