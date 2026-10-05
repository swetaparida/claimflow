package com.insurer.claimflow.intake.adapter.in.web;

import com.insurer.claimflow.lifecycle.domain.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "SubmitClaimRequest")
public record SubmitClaimRequest(
        @NotBlank @Size(max = 32) @Schema(example = "POL-1001") String policyNumber,
        @NotNull @Valid ClaimantRequest claimant,
        @NotNull @Valid IncidentRequest incident,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) @Schema(example = "4500.00") BigDecimal claimedAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code") @Schema(example = "EUR") String currency) {

    public record ClaimantRequest(
            @NotBlank @Size(max = 200) @Schema(example = "Jane Doe") String name,
            @NotBlank @Email @Size(max = 320) @Schema(example = "jane.doe@example.com") String email,
            @Size(max = 32) String phone) {
    }

    public record IncidentRequest(
            @NotNull IncidentType type,
            @NotNull @PastOrPresent LocalDate date,
            @Size(max = 500) String location,
            @NotBlank @Size(max = 4000) String description) {
    }
}
