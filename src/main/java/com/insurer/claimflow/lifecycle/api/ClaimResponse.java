package com.insurer.claimflow.lifecycle.api;

import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import com.insurer.claimflow.lifecycle.domain.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "Claim")
public record ClaimResponse(
        UUID id,
        String claimNumber,
        String policyNumber,
        ClaimStatus status,
        ClaimantDto claimant,
        IncidentDto incident,
        BigDecimal claimedAmount,
        BigDecimal reserveAmount,
        BigDecimal approvedAmount,
        BigDecimal settledAmount,
        String currency,
        String assignedOfficerId,
        String decisionReason,
        String paymentReference,
        Instant createdAt,
        Instant updatedAt) {

    public record ClaimantDto(String name, String email, String phone) {
    }

    public record IncidentDto(IncidentType type, LocalDate date, String location, String description) {
    }

    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(c.getId(), c.getClaimNumber(), c.getPolicyNumber(), c.getStatus(),
                new ClaimantDto(c.getClaimant().name(), c.getClaimant().email(), c.getClaimant().phone()),
                new IncidentDto(c.getIncident().type(), c.getIncident().date(), c.getIncident().location(),
                        c.getIncident().description()),
                c.getClaimedAmount(), c.getReserveAmount(), c.getApprovedAmount(), c.getSettledAmount(),
                c.getCurrency(), c.getAssignedOfficerId(), c.getDecisionReason(), c.getPaymentReference(),
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
