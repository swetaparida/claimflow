package com.insurer.claimflow.notification.application.port.out;

import java.util.UUID;

/**
 * Idempotency store for at-least-once message delivery.
 */
public interface ProcessedEventPort {

    /**
     * Atomically records that {@code consumer} handled {@code eventId}.
     *
     * @return {@code true} if this is the first time, {@code false} if it was already processed
     */
    boolean markProcessed(UUID eventId, String consumer);
}
