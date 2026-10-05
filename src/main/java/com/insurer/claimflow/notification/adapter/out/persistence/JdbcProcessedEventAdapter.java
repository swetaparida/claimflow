package com.insurer.claimflow.notification.adapter.out.persistence;

import com.insurer.claimflow.notification.application.port.out.ProcessedEventPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Component
class JdbcProcessedEventAdapter implements ProcessedEventPort {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcProcessedEventAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public boolean markProcessed(UUID eventId, String consumer) {
        int inserted = jdbc.update("""
                INSERT INTO processed_event (event_id, consumer, processed_at)
                VALUES (?, ?, ?)
                ON CONFLICT (event_id, consumer) DO NOTHING
                """, eventId, consumer, Timestamp.from(clock.instant()));
        return inserted == 1;
    }
}
