package com.insurer.claimflow.shared.outbox.adapter.out.persistence;

import com.insurer.claimflow.shared.outbox.application.OutboxStorePort;
import com.insurer.claimflow.shared.outbox.domain.OutboxMessage;
import org.springframework.stereotype.Component;

@Component
class JpaOutboxStoreAdapter implements OutboxStorePort {

    private final OutboxEventJpaRepository repository;

    JpaOutboxStoreAdapter(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(OutboxMessage m) {
        repository.save(new OutboxEventJpaEntity(m.id(), m.aggregateType(), m.aggregateId(), m.eventType(),
                m.topic(), m.payload(), m.createdAt()));
    }
}
