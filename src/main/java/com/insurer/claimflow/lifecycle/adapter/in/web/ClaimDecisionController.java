package com.insurer.claimflow.lifecycle.adapter.in.web;

import com.insurer.claimflow.lifecycle.api.ClaimResponse;
import com.insurer.claimflow.lifecycle.adapter.in.web.DecisionRequests.ApproveClaimRequest;
import com.insurer.claimflow.lifecycle.adapter.in.web.DecisionRequests.RejectClaimRequest;
import com.insurer.claimflow.lifecycle.adapter.in.web.DecisionRequests.SettleClaimRequest;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.ApproveClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.RejectClaimCommand;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.SettleClaimCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{id}")
@Tag(name = "Lifecycle")
class ClaimDecisionController {

    private final ClaimDecisionUseCase decisions;

    ClaimDecisionController(ClaimDecisionUseCase decisions) {
        this.decisions = decisions;
    }

    @PostMapping("/approve")
    @Operation(summary = "Approve a claim that is under review")
    @ApiResponse(responseCode = "200", description = "Claim approved")
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW")
    @ApiResponse(responseCode = "422", description = "Approved amount invalid")
    ClaimResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveClaimRequest request) {
        return ClaimResponse.from(decisions.approve(
                new ApproveClaimCommand(id, request.approvedAmount(), request.notes(), request.decidedBy())));
    }

    @PostMapping("/reject")
    @Operation(summary = "Reject a claim that is under review")
    @ApiResponse(responseCode = "200", description = "Claim rejected")
    @ApiResponse(responseCode = "409", description = "Claim is not UNDER_REVIEW")
    ClaimResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectClaimRequest request) {
        return ClaimResponse.from(decisions.reject(new RejectClaimCommand(id, request.reason(), request.decidedBy())));
    }

    @PostMapping("/settle")
    @Operation(summary = "Settle (pay) an approved claim")
    @ApiResponse(responseCode = "200", description = "Claim settled")
    @ApiResponse(responseCode = "409", description = "Claim is not APPROVED")
    @ApiResponse(responseCode = "422", description = "Settlement exceeds approved amount")
    ClaimResponse settle(@PathVariable UUID id, @Valid @RequestBody SettleClaimRequest request) {
        return ClaimResponse.from(decisions.settle(new SettleClaimCommand(id, request.settlementAmount(),
                request.paymentReference(), request.settledBy())));
    }
}
