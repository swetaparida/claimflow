package com.insurer.claimflow.shared.outbox.adapter.out.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tuning knobs for the outbox relay.
 *
 * @param enabled      whether this instance relays outbox events
 * @param pollInterval delay between relay runs
 * @param batchSize    max events locked per run
 * @param maxAttempts  attempts before an event is parked as FAILED
 * @param sendTimeout  max wait for a broker acknowledgement
 * @param retention    how long PUBLISHED events are kept before cleanup
 */
@ConfigurationProperties(prefix = "claimflow.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("500ms") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10") int maxAttempts,
        @DefaultValue("10s") Duration sendTimeout,
        @DefaultValue("7d") Duration retention) {
}
