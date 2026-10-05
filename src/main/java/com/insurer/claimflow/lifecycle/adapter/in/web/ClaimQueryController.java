package com.insurer.claimflow.lifecycle.adapter.in.web;

import com.insurer.claimflow.lifecycle.api.ClaimResponse;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimQueryUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimSearchCriteria;
import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims")
@Tag(name = "Claims")
class ClaimQueryController {

    private final ClaimQueryUseCase claims;

    ClaimQueryController(ClaimQueryUseCase claims) {
        this.claims = claims;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a claim by id")
    @ApiResponse(responseCode = "200", description = "Claim found")
    @ApiResponse(responseCode = "404", description = "Claim not found")
    ClaimResponse getClaim(@PathVariable UUID id) {
        return ClaimResponse.from(claims.getClaim(id));
    }

    @GetMapping
    @Operation(summary = "Search claims", description = "Paged list, newest first, optionally filtered.")
    PageResponse<ClaimResponse> search(@RequestParam(required = false) ClaimStatus status,
                                       @RequestParam(required = false) String assignedOfficerId,
                                       @RequestParam(required = false) String policyNumber,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(claims.search(new ClaimSearchCriteria(status, assignedOfficerId, policyNumber), page, size)
                .map(ClaimResponse::from));
    }
}
