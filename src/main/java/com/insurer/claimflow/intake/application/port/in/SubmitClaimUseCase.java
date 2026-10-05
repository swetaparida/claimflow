package com.insurer.claimflow.intake.application.port.in;

import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.IncidentType;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface SubmitClaimUseCase {

    Claim submit(SubmitClaimCommand command);

    record SubmitClaimCommand(
            String policyNumber,
            String claimantName,
            String claimantEmail,
            String claimantPhone,
            IncidentType incidentType,
            LocalDate incidentDate,
            String incidentLocation,
            String incidentDescription,
            BigDecimal claimedAmount,
            String currency,
            String submittedBy) {
    }
}
