package com.insurer.claimflow.lifecycle.adapter.out.persistence;

import com.insurer.claimflow.lifecycle.application.port.out.ClaimNumberGeneratorPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Generates gap-tolerant, unique claim numbers from a PostgreSQL sequence.
 */
@Component
class SequenceClaimNumberGenerator implements ClaimNumberGeneratorPort {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    SequenceClaimNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public String nextClaimNumber() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('claim_number_seq')", Long.class);
        return "CLM-%d-%08d".formatted(Year.now(clock).getValue(), next);
    }
}
