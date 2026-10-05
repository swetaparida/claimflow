package com.insurer.claimflow.shared.outbox.application;

import com.insurer.claimflow.shared.outbox.domain.OutboxMessage;

/**
 * Outbound port used to persist outbox messages in the caller's transaction.
 */
public interface OutboxStorePort {

    void save(OutboxMessage message);
}
