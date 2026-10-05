package com.insurer.claimflow.intake.adapter.in.web;

import com.insurer.claimflow.intake.application.port.in.SubmitClaimUseCase;
import com.insurer.claimflow.intake.application.port.in.SubmitClaimUseCase.SubmitClaimCommand;
import com.insurer.claimflow.lifecycle.api.ClaimResponse;
import com.insurer.claimflow.lifecycle.domain.Claim;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claim Intake")
class ClaimIntakeController {

    private final SubmitClaimUseCase submitClaim;

    ClaimIntakeController(SubmitClaimUseCase submitClaim) {
        this.submitClaim = submitClaim;
    }

    @PostMapping
    @Operation(summary = "Submit a new claim (first notification of loss)")
    @ApiResponse(responseCode = "201", description = "Claim registered in status REPORTED")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "422", description = "Policy does not cover the claim")
    ResponseEntity<ClaimResponse> submit(@Valid @RequestBody SubmitClaimRequest request,
                                         @RequestHeader(name = "X-User-Id", required = false) String userId) {
        Claim claim = submitClaim.submit(new SubmitClaimCommand(
                request.policyNumber(),
                request.claimant().name(),
                request.claimant().email(),
                request.claimant().phone(),
                request.incident().type(),
                request.incident().date(),
                request.incident().location(),
                request.incident().description(),
                request.claimedAmount(),
                request.currency(),
                userId));
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(claim.getId()).toUri())
                .body(ClaimResponse.from(claim));
    }
}
