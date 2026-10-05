package com.insurer.claimflow.lifecycle.application.port.in;

import com.insurer.claimflow.lifecycle.domain.Claim;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Decision and settlement use cases of the Lifecycle Management module.
 */
public interface ClaimDecisionUseCase {

    Claim approve(ApproveClaimCommand command);

    Claim reject(RejectClaimCommand command);

    Claim settle(SettleClaimCommand command);

    record ApproveClaimCommand(UUID claimId, BigDecimal approvedAmount, String notes, String decidedBy) {
    }

    record RejectClaimCommand(UUID claimId, String reason, String decidedBy) {
    }

    record SettleClaimCommand(UUID claimId, BigDecimal settlementAmount, String paymentReference, String settledBy) {
    }
}
