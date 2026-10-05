package com.insurer.claimflow.assessment.adapter.in.web;

import com.insurer.claimflow.assessment.application.port.in.RecordAssessmentUseCase;
import com.insurer.claimflow.assessment.application.port.in.RecordAssessmentUseCase.RecordAssessmentCommand;
import com.insurer.claimflow.assessment.domain.Assessment;
import com.insurer.claimflow.assessment.domain.AssessmentOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{id}/assessments")
@Tag(name = "Assessment")
class AssessmentController {

    record RecordAssessmentRequest(
            @NotBlank @Size(max = 64) String assessorId,
            @NotNull AssessmentOutcome outcome,
            @NotBlank @Size(max = 4000) String findings,
            @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal recommendedReserve) {
    }

    record AssessmentResponse(UUID id, UUID claimId, String assessorId, AssessmentOutcome outcome, String findings,
                              BigDecimal recommendedReserve, Instant assessedAt) {
        static AssessmentResponse from(Assessment a) {
            return new AssessmentResponse(a.id(), a.claimId(), a.assessorId(), a.outcome(), a.findings(),
                    a.recommendedReserve(), a.assessedAt());
        }
    }

    private final RecordAssessmentUseCase assessments;

    AssessmentController(RecordAssessmentUseCase assessments) {
        this.assessments = assessments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record an assessment; moves the claim to UNDER_REVIEW (or INFO_REQUIRED)")
    @ApiResponse(responseCode = "201", description = "Assessment recorded")
    @ApiResponse(responseCode = "409", description = "Claim not assigned yet, or already decided")
    @ApiResponse(responseCode = "422", description = "Assessor is not the assigned officer")
    AssessmentResponse record(@PathVariable UUID id, @Valid @RequestBody RecordAssessmentRequest request) {
        return AssessmentResponse.from(assessments.record(new RecordAssessmentCommand(id, request.assessorId(),
                request.outcome(), request.findings(), request.recommendedReserve())));
    }

    @GetMapping
    @Operation(summary = "List assessments of a claim")
    List<AssessmentResponse> list(@PathVariable UUID id) {
        return assessments.assessmentsOf(id).stream().map(AssessmentResponse::from).toList();
    }
}
