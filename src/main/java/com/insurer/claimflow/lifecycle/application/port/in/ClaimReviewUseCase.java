package com.insurer.claimflow.lifecycle.application.port.in;

import com.insurer.claimflow.lifecycle.domain.Claim;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Review and reserve use cases of the Lifecycle Management module.
 *
 * <p>Recording an assessment also puts a claim under review and may adjust the reserve, but an officer
 * must be able to do both explicitly: picking a claim up before any findings exist, and re-reserving as
 * new information arrives without filing another assessment.
 */
public interface ClaimReviewUseCase {

    /** Moves an ASSIGNED (or INFO_REQUIRED) claim to UNDER_REVIEW. Idempotent. */
    Claim beginReview(BeginReviewCommand command);

    /** Sets the case reserve on an open claim, emitting {@code ReserveChanged}. */
    Claim adjustReserve(AdjustReserveCommand command);

    record BeginReviewCommand(UUID claimId, String reviewerId) {
    }

    record AdjustReserveCommand(UUID claimId, BigDecimal reserveAmount, String reason, String actor) {
    }
}
