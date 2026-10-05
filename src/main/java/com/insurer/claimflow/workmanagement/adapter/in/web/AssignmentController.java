package com.insurer.claimflow.workmanagement.adapter.in.web;

import com.insurer.claimflow.workmanagement.application.port.in.AssignClaimUseCase;
import com.insurer.claimflow.workmanagement.application.port.in.AssignClaimUseCase.AssignClaimCommand;
import com.insurer.claimflow.workmanagement.domain.Assignment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{id}")
@Tag(name = "Work Management")
class AssignmentController {

    record AssignClaimRequest(
            @NotBlank @Size(max = 64) String officerId,
            @NotBlank @Size(max = 64) String assignedBy,
            @Size(max = 1000) String note) {
    }

    record AssignmentResponse(UUID id, UUID claimId, String officerId, String assignedBy, String note,
                              Instant assignedAt, Instant releasedAt, boolean active) {
        static AssignmentResponse from(Assignment a) {
            return new AssignmentResponse(a.getId(), a.getClaimId(), a.getOfficerId(), a.getAssignedBy(), a.getNote(),
                    a.getAssignedAt(), a.getReleasedAt(), a.isActive());
        }
    }

    private final AssignClaimUseCase assignClaim;

    AssignmentController(AssignClaimUseCase assignClaim) {
        this.assignClaim = assignClaim;
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign or re-assign a claim to a claims officer")
    @ApiResponse(responseCode = "200", description = "Claim assigned")
    @ApiResponse(responseCode = "409", description = "Claim is in a status that cannot be assigned")
    @ApiResponse(responseCode = "422", description = "Claim already assigned to this officer")
    AssignmentResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignClaimRequest request) {
        return AssignmentResponse.from(assignClaim.assign(
                new AssignClaimCommand(id, request.officerId(), request.assignedBy(), request.note())));
    }

    @GetMapping("/assignments")
    @Operation(summary = "Assignment history of a claim")
    List<AssignmentResponse> assignments(@PathVariable UUID id) {
        return assignClaim.assignmentsOf(id).stream().map(AssignmentResponse::from).toList();
    }
}
