package com.insurer.claimflow.lifecycle.adapter.in.web;

import com.insurer.claimflow.lifecycle.api.ClaimResponse;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimReviewUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimReviewUseCase.AdjustReserveCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimReviewUseCase.BeginReviewCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lets an officer explicitly start the review of a claim and re-reserve it, independently of filing
 * an assessment.
 */
@RestController
@RequestMapping("/api/v1/claims/{id}")
@Tag(name = "Lifecycle")
class ClaimReviewController {

    record BeginReviewRequest(
            @NotBlank @Size(max = 64) String reviewerId) {
    }

    record AdjustReserveRequest(
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal reserveAmount,
            @NotBlank @Size(max = 255) String reason,
            @NotBlank @Size(max = 64) String actor) {
    }

    private final ClaimReviewUseCase review;

    ClaimReviewController(ClaimReviewUseCase review) {
        this.review = review;
    }

    @PostMapping("/review")
    @Operation(summary = "Start the review of an assigned claim",
            description = "Moves the claim to UNDER_REVIEW. Idempotent: a claim already under review is unchanged.")
    @ApiResponse(responseCode = "200", description = "Claim is under review")
    @ApiResponse(responseCode = "409", description = "Claim is not assigned yet, or already decided")
    ClaimResponse beginReview(@PathVariable UUID id, @Valid @RequestBody BeginReviewRequest request) {
        return ClaimResponse.from(review.beginReview(new BeginReviewCommand(id, request.reviewerId())));
    }

    @PostMapping("/reserve")
    @Operation(summary = "Set the case reserve of an open claim",
            description = "Emits ReserveChanged. Rejected for claims that are already decided or settled.")
    @ApiResponse(responseCode = "200", description = "Reserve updated")
    @ApiResponse(responseCode = "422", description = "Claim is closed, or the reserve is negative")
    ClaimResponse adjustReserve(@PathVariable UUID id, @Valid @RequestBody AdjustReserveRequest request) {
        return ClaimResponse.from(review.adjustReserve(
                new AdjustReserveCommand(id, request.reserveAmount(), request.reason(), request.actor())));
    }
}
