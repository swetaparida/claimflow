package com.insurer.claimflow.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end REST tests against real PostgreSQL (Flyway schema, JPA mappings, optimistic locking).
 */
class ClaimLifecycleIT extends AbstractIntegrationTest {

    ClaimApi api;

    @BeforeEach
    void setUp() {
        api = new ClaimApi(mvc);
    }

    @Test
    void happyPathFromReportToSettlement() throws Exception {
        api.post("/api/v1/claims", ClaimApi.submitBody("POL-1001", "4500.00", "EUR"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/claims/")))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.claimNumber").value(containsString("CLM-")))
                .andExpect(jsonPath("$.reserveAmount").value(4500.00));

        UUID id = api.submit("4500.00");

        api.assign(id, "officer-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerId").value("officer-1"));
        api.get("/api/v1/claims/{id}", id).andExpect(jsonPath("$.status").value("ASSIGNED"));

        api.assess(id, "officer-1", "RECOMMEND_APPROVAL", "4200.00").andExpect(status().isCreated());
        api.get("/api/v1/claims/{id}", id)
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reserveAmount").value(4200.00));

        api.approve(id, "4000.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvedAmount").value(4000.00));

        api.settle(id, "4000.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.reserveAmount").value(0))
                .andExpect(jsonPath("$.settledAmount").value(4000.00));

        api.get("/api/v1/claims/{id}/history", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ClaimCreated"))
                .andExpect(jsonPath("$[?(@.eventType == 'ClaimStatusChanged')].toStatus")
                        .value(org.hamcrest.Matchers.contains("ASSIGNED", "UNDER_REVIEW", "APPROVED", "SETTLED")));
    }

    /**
     * The officer-driven path: start the review explicitly and re-reserve the claim, independently of
     * filing an assessment.
     */
    @Test
    void officerStartsReviewAndUpdatesReserve() throws Exception {
        UUID id = api.submit("3000.00");

        // Review requires an assignment first.
        api.beginReview(id, "officer-7")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("REPORTED"))
                .andExpect(jsonPath("$.targetStatus").value("UNDER_REVIEW"));

        api.assign(id, "officer-7").andExpect(status().isOk());

        api.beginReview(id, "officer-7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // Idempotent: starting the review again changes nothing.
        api.beginReview(id, "officer-7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        api.adjustReserve(id, "2500.00", "officer-7")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserveAmount").value(2500.00));

        // A negative reserve never reaches the domain - bean validation rejects it.
        api.adjustReserve(id, "-1.00", "officer-7")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        api.get("/api/v1/claims/{id}/history", id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'ReserveChanged')]").isNotEmpty());

        // Once the claim is closed the reserve is frozen.
        api.approve(id, "2500.00").andExpect(status().isOk());
        api.settle(id, "2500.00").andExpect(status().isOk());
        api.adjustReserve(id, "100.00", "officer-7")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLAIM_CLOSED"));
        api.beginReview(id, "officer-7").andExpect(status().isConflict());
    }

    @Test
    void rejectionPath() throws Exception {
        UUID id = api.underReview("1000.00", "officer-2");

        api.reject(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reserveAmount").value(0));

        api.settle(id, "10.00").andExpect(status().isConflict());
    }

    @Test
    void infoRequiredThenResumeReview() throws Exception {
        UUID id = api.submit("800.00");
        api.assign(id, "officer-3").andExpect(status().isOk());

        api.assess(id, "officer-3", "INFO_REQUIRED", null).andExpect(status().isCreated());
        api.get("/api/v1/claims/{id}", id).andExpect(jsonPath("$.status").value("INFO_REQUIRED"));
        api.approve(id, "100.00").andExpect(status().isConflict());

        api.assess(id, "officer-3", "RECOMMEND_APPROVAL", null).andExpect(status().isCreated());
        api.approve(id, "800.00").andExpect(status().isOk());
    }

    @Test
    void approvingReportedClaimReturns409() throws Exception {
        UUID id = api.submit("500.00");

        api.approve(id, "500.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.currentStatus").value("REPORTED"))
                .andExpect(jsonPath("$.targetStatus").value("APPROVED"));
    }

    @Test
    void assessingUnassignedClaimReturns409() throws Exception {
        UUID id = api.submit("500.00");

        api.assess(id, "officer-1", "RECOMMEND_APPROVAL", null).andExpect(status().isConflict());
        api.get("/api/v1/claims/{id}/assessments", id).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void settlingBeforeApprovalReturns409() throws Exception {
        UUID id = api.underReview("500.00", "officer-1");

        api.settle(id, "500.00").andExpect(status().isConflict());
    }

    @Test
    void approvalAboveClaimedAmountReturns422() throws Exception {
        UUID id = api.underReview("500.00", "officer-1");

        api.approve(id, "500.01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVED_AMOUNT_EXCEEDS_CLAIM"));
    }

    @Test
    void reassignmentKeepsSingleActiveAssignment() throws Exception {
        UUID id = api.submit("500.00");
        api.assign(id, "officer-1").andExpect(status().isOk());
        api.assign(id, "officer-2").andExpect(status().isOk());
        api.assign(id, "officer-2").andExpect(status().isUnprocessableEntity());

        api.get("/api/v1/claims/{id}/assignments", id)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.active == true)].officerId").value(org.hamcrest.Matchers.contains("officer-2")));
        api.get("/api/v1/claims/{id}", id).andExpect(jsonPath("$.assignedOfficerId").value("officer-2"));
    }

    @Test
    void submissionValidation() throws Exception {
        api.post("/api/v1/claims", "{\"policyNumber\": \"\", \"claimedAmount\": -1, \"currency\": \"euro\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("policyNumber")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("currency")));

        api.post("/api/v1/claims", ClaimApi.submitBody("POL-9999", "100.00", "EUR"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));

        api.post("/api/v1/claims", ClaimApi.submitBody("POL-2001", "100.00", "EUR"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_ACTIVE"));

        api.post("/api/v1/claims", ClaimApi.submitBody("POL-1001", "100.00", "USD"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    }

    @Test
    void unknownClaimReturns404() throws Exception {
        api.get("/api/v1/claims/{id}", UUID.randomUUID()).andExpect(status().isNotFound());
        api.approve(UUID.randomUUID(), "1.00").andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByStatusAndOfficer() throws Exception {
        String officer = "officer-" + UUID.randomUUID().toString().substring(0, 8);
        UUID id = api.submit("700.00");
        api.assign(id, officer).andExpect(status().isOk());

        api.get("/api/v1/claims?assignedOfficerId={o}&status=ASSIGNED", officer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));

        api.get("/api/v1/claims?status=REPORTED&size=5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", everyItem(is("REPORTED"))))
                .andExpect(jsonPath("$.size").value(5));

        api.get("/api/v1/claims?status=NOPE").andExpect(status().isBadRequest());
    }
}
