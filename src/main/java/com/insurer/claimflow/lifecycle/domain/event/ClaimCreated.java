package com.insurer.claimflow.lifecycle.domain.event;

import com.insurer.claimflow.lifecycle.domain.IncidentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimCreated(
        UUID eventId,
        UUID claimId,
        String claimNumber,
        String policyNumber,
        String claimantName,
        String claimantEmail,
        IncidentType incidentType,
        LocalDate incidentDate,
        BigDecimal claimedAmount,
        String currency,
        String actor,
        Instant occurredAt) implements ClaimDomainEvent {
}
