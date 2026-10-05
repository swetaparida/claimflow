package com.insurer.claimflow.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Small HTTP DSL over MockMvc used by the integration tests.
 */
final class ClaimApi {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MockMvc mvc;

    ClaimApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    static String submitBody(String policyNumber, String amount, String currency) {
        return """
                {
                  "policyNumber": "%s",
                  "claimant": {"name": "Jane Doe", "email": "jane.doe@example.com", "phone": "+49 30 1234567"},
                  "incident": {"type": "AUTO_COLLISION", "date": "%s", "location": "Berlin",
                               "description": "Rear-ended at a traffic light"},
                  "claimedAmount": %s,
                  "currency": "%s"
                }
                """.formatted(policyNumber, LocalDate.now().minusDays(2), amount, currency);
    }

    ResultActions post(String path, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    ResultActions get(String path, Object... vars) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path, vars));
    }

    UUID submit(String amount) throws Exception {
        return submit("POL-1001", amount, "EUR");
    }

    UUID submit(String policy, String amount, String currency) throws Exception {
        String body = post("/api/v1/claims", submitBody(policy, amount, currency))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(read(body).path("id").asText());
    }

    ResultActions assign(UUID id, String officer) throws Exception {
        return post("/api/v1/claims/" + id + "/assign",
                "{\"officerId\": \"%s\", \"assignedBy\": \"supervisor-1\"}".formatted(officer));
    }

    ResultActions assess(UUID id, String officer, String outcome, String reserve) throws Exception {
        return post("/api/v1/claims/" + id + "/assessments", """
                {"assessorId": "%s", "outcome": "%s", "findings": "Inspected vehicle", "recommendedReserve": %s}
                """.formatted(officer, outcome, reserve == null ? "null" : reserve));
    }

    ResultActions beginReview(UUID id, String reviewer) throws Exception {
        return post("/api/v1/claims/" + id + "/review", "{\"reviewerId\": \"%s\"}".formatted(reviewer));
    }

    ResultActions adjustReserve(UUID id, String amount, String actor) throws Exception {
        return post("/api/v1/claims/" + id + "/reserve",
                "{\"reserveAmount\": %s, \"reason\": \"Revised repair estimate\", \"actor\": \"%s\"}"
                        .formatted(amount, actor));
    }

    ResultActions approve(UUID id, String amount) throws Exception {
        return post("/api/v1/claims/" + id + "/approve",
                "{\"approvedAmount\": %s, \"decidedBy\": \"manager-1\", \"notes\": \"Covered\"}".formatted(amount));
    }

    ResultActions reject(UUID id) throws Exception {
        return post("/api/v1/claims/" + id + "/reject",
                "{\"reason\": \"Policy exclusion\", \"decidedBy\": \"manager-1\"}");
    }

    ResultActions settle(UUID id, String amount) throws Exception {
        return post("/api/v1/claims/" + id + "/settle",
                "{\"settlementAmount\": %s, \"paymentReference\": \"PAY-%s\", \"settledBy\": \"finance-1\"}"
                        .formatted(amount, UUID.randomUUID().toString().substring(0, 8)));
    }

    /** Takes a claim from REPORTED to UNDER_REVIEW. */
    UUID underReview(String amount, String officer) throws Exception {
        UUID id = submit(amount);
        assign(id, officer).andExpect(status().isOk());
        assess(id, officer, "RECOMMEND_APPROVAL", null).andExpect(status().isCreated());
        return id;
    }

    static JsonNode read(String json) throws Exception {
        return JSON.readTree(json);
    }
}
