package com.insurer.claimflow.lifecycle.adapter.in.web;

import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase.ApproveClaimCommand;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.ClaimFixtures;
import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import com.insurer.claimflow.shared.domain.InvalidStateTransitionException;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ClaimDecisionController.class)
class ClaimDecisionControllerTest {

    private static final String APPROVE_BODY = """
            {"approvedAmount": 1200.00, "decidedBy": "manager-1", "notes": "Covered"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ClaimDecisionUseCase decisions;

    @Test
    void approveReturnsUpdatedClaim() throws Exception {
        Claim claim = ClaimFixtures.underReview();
        claim.approve(new BigDecimal("1200.00"), "Covered", "manager-1", ClaimFixtures.NOW);
        when(decisions.approve(any(ApproveClaimCommand.class))).thenReturn(claim);

        mvc.perform(post("/api/v1/claims/{id}/approve", claim.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(1200.00));
    }

    @Test
    void invalidTransitionMapsTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(decisions.approve(any())).thenThrow(new InvalidStateTransitionException("Claim", id,
                ClaimStatus.REPORTED.name(), ClaimStatus.APPROVED.name()));

        mvc.perform(post("/api/v1/claims/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("REPORTED"))
                .andExpect(jsonPath("$.targetStatus").value("APPROVED"));
    }

    @Test
    void businessRuleViolationMapsTo422() throws Exception {
        when(decisions.approve(any())).thenThrow(
                new BusinessRuleViolationException("APPROVED_AMOUNT_EXCEEDS_CLAIM", "too much"));

        mvc.perform(post("/api/v1/claims/{id}/approve", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVED_AMOUNT_EXCEEDS_CLAIM"));
    }

    @Test
    void unknownClaimMapsTo404() throws Exception {
        UUID id = UUID.randomUUID();
        when(decisions.approve(any())).thenThrow(new ResourceNotFoundException("Claim", id));

        mvc.perform(post("/api/v1/claims/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON).content(APPROVE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPayloadMapsTo400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/claims/{id}/settle", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementAmount\": -5, \"paymentReference\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
        verifyNoInteractions(decisions);
    }

    @Test
    void malformedIdMapsTo400() throws Exception {
        mvc.perform(post("/api/v1/claims/{id}/reject", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"x\", \"decidedBy\": \"m\"}"))
                .andExpect(status().isBadRequest());
    }
}
