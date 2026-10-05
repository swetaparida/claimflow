package com.insurer.claimflow.lifecycle.adapter.in.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class DecisionRequests {

    private DecisionRequests() {
    }

    public record ApproveClaimRequest(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal approvedAmount,
            @NotBlank @Size(max = 64) String decidedBy,
            @Size(max = 2000) String notes) {
    }

    public record RejectClaimRequest(
            @NotBlank @Size(max = 2000) String reason,
            @NotBlank @Size(max = 64) String decidedBy) {
    }

    public record SettleClaimRequest(
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal settlementAmount,
            @NotBlank @Size(max = 64) String paymentReference,
            @NotBlank @Size(max = 64) String settledBy) {
    }
}
