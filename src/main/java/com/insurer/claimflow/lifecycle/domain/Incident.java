package com.insurer.claimflow.lifecycle.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The loss event behind a claim. Immutable once reported.
 */
public record Incident(UUID id, IncidentType type, LocalDate date, String location, String description) {

    public Incident {
        Objects.requireNonNull(id, "incident id is required");
        Objects.requireNonNull(type, "incident type is required");
        Objects.requireNonNull(date, "incident date is required");
        Objects.requireNonNull(description, "incident description is required");
    }
}
