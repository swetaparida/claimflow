package com.insurer.claimflow.lifecycle.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurer.claimflow.audit.application.port.in.AuditTrailUseCase;
import com.insurer.claimflow.audit.application.port.in.AuditTrailUseCase.RecordHistoryCommand;
import com.insurer.claimflow.lifecycle.application.port.out.ClaimRepositoryPort;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.ClaimFixtures;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import com.insurer.claimflow.shared.messaging.Topics;
import com.insurer.claimflow.shared.outbox.application.OutboxStorePort;
import com.insurer.claimflow.shared.outbox.application.OutboxWriter;
import com.insurer.claimflow.shared.outbox.domain.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimAggregateStoreTest {

    @Mock
    ClaimRepositoryPort repository;
    @Mock
    OutboxStorePort outboxStore;
    @Mock
    AuditTrailUseCase auditTrail;

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ClaimAggregateStore store;

    @BeforeEach
    void setUp() {
        store = new ClaimAggregateStore(repository, new OutboxWriter(outboxStore, objectMapper), auditTrail);
    }

    @Test
    void loadThrowsNotFoundForUnknownClaim() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.load(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveWritesEveryEventToOutboxAndAuditTrail() throws Exception {
        Claim claim = ClaimFixtures.reported();
        claim.assignTo("officer-7", "supervisor", ClaimFixtures.NOW);

        store.save(claim);

        verify(repository).save(claim);
        ArgumentCaptor<OutboxMessage> outbox = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxStore, times(2)).save(outbox.capture());
        assertThat(outbox.getAllValues()).extracting(OutboxMessage::topic)
                .containsExactly(Topics.CLAIM_ASSIGNED, Topics.STATUS_CHANGED);
        assertThat(outbox.getAllValues()).allSatisfy(m -> {
            assertThat(m.aggregateId()).isEqualTo(claim.getId());
            assertThat(m.aggregateType()).isEqualTo("Claim");
        });

        JsonNode envelope = objectMapper.readTree(outbox.getAllValues().get(1).payload());
        assertThat(envelope.path("eventType").asText()).isEqualTo("ClaimStatusChanged");
        assertThat(envelope.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.path("data").path("toStatus").asText()).isEqualTo("ASSIGNED");

        ArgumentCaptor<RecordHistoryCommand> history = ArgumentCaptor.forClass(RecordHistoryCommand.class);
        verify(auditTrail, times(2)).record(history.capture());
        RecordHistoryCommand statusChange = history.getAllValues().get(1);
        assertThat(statusChange.fromStatus()).isEqualTo("REPORTED");
        assertThat(statusChange.toStatus()).isEqualTo("ASSIGNED");
        assertThat(statusChange.actor()).isEqualTo("supervisor");

        assertThat(claim.peekDomainEvents()).isEmpty();
    }

    @Test
    void newClaimEventsRouteToCreatedAndReserveTopics() {
        Claim claim = Claim.submit(UUID.randomUUID(), "CLM-1", "POL-1001",
                ClaimFixtures.reported().getClaimant(), ClaimFixtures.reported().getIncident(),
                new java.math.BigDecimal("100"), "EUR", "claimant", ClaimFixtures.NOW);

        store.save(claim);

        ArgumentCaptor<OutboxMessage> outbox = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outboxStore, times(2)).save(outbox.capture());
        assertThat(outbox.getAllValues()).extracting(OutboxMessage::topic)
                .isEqualTo(List.of(Topics.CLAIM_CREATED, Topics.RESERVE_CHANGED));
    }
}
