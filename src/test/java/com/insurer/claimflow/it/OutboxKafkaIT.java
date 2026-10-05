package com.insurer.claimflow.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.insurer.claimflow.notification.application.NotificationService;
import com.insurer.claimflow.shared.messaging.Topics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the transactional outbox end to end: business change → outbox_event row → relay → Kafka topic →
 * notification consumer (idempotent, recorded in processed_event).
 */
class OutboxKafkaIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void lifecycleEventsArePublishedToKafkaKeyedByClaimId() throws Exception {
        ClaimApi api = new ClaimApi(mvc);
        UUID id = api.submit("2500.00");
        api.assign(id, "officer-9").andExpect(status().isOk());
        api.assess(id, "officer-9", "RECOMMEND_APPROVAL", null).andExpect(status().isCreated());
        api.approve(id, "2000.00").andExpect(status().isOk());

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(Topics.ALL);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (id.toString().equals(r.key())) {
                        received.add(r);
                    }
                });
                // created, reserve(initial), assigned, status→ASSIGNED, status→UNDER_REVIEW,
                // status→APPROVED, decision, reserve(approval)
                assertThat(received).hasSize(8);
            });
        }

        assertThat(received).extracting(ConsumerRecord::topic).contains(Topics.ALL.toArray(String[]::new));
        ConsumerRecord<String, String> created = received.stream()
                .filter(r -> r.topic().equals(Topics.CLAIM_CREATED)).findFirst().orElseThrow();
        JsonNode envelope = ClaimApi.read(created.value());
        assertThat(envelope.path("eventType").asText()).isEqualTo("ClaimCreated");
        assertThat(envelope.path("aggregateId").asText()).isEqualTo(id.toString());
        assertThat(envelope.path("data").path("policyNumber").asText()).isEqualTo("POL-1001");
        assertThat(new String(created.headers().lastHeader(Topics.HEADER_EVENT_ID).value(), StandardCharsets.UTF_8))
                .isEqualTo(envelope.path("eventId").asText());

        // All events for one claim share a partition, so their relative order is preserved.
        List<String> statusFlow = received.stream()
                .filter(r -> r.topic().equals(Topics.STATUS_CHANGED))
                .map(r -> readUnchecked(r.value()).path("data").path("toStatus").asText())
                .toList();
        assertThat(statusFlow).containsExactly("ASSIGNED", "UNDER_REVIEW", "APPROVED");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND status <> 'PUBLISHED'",
                Long.class, id)).isZero());

        // created, assigned, 3 status changes and the decision are on topics the notification module consumes.
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM processed_event p
                JOIN outbox_event o ON o.id = p.event_id
                WHERE o.aggregate_id = ? AND p.consumer = ?
                """, Long.class, id, NotificationService.CONSUMER_NAME)).isEqualTo(6L));
    }

    @Test
    void failedBusinessTransactionWritesNoOutboxEvents() throws Exception {
        ClaimApi api = new ClaimApi(mvc);
        UUID id = api.submit("300.00");
        Long before = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", Long.class, id);

        api.approve(id, "300.00").andExpect(status().isConflict());

        Long after = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", Long.class, id);
        assertThat(after).isEqualTo(before);
    }

    private static JsonNode readUnchecked(String json) {
        try {
            return ClaimApi.read(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
