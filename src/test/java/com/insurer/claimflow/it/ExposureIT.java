package com.insurer.claimflow.it;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exposure figures are global aggregates, so assertions are made on deltas to stay independent of other tests.
 */
class ExposureIT extends AbstractIntegrationTest {

    ClaimApi api;

    @BeforeEach
    void setUp() {
        api = new ClaimApi(mvc);
    }

    private JsonNode exposure() throws Exception {
        return ClaimApi.read(api.get("/api/v1/exposure")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static JsonNode currency(JsonNode report, String ccy) {
        for (JsonNode node : report.path("byCurrency")) {
            if (ccy.equals(node.path("currency").asText())) {
                return node;
            }
        }
        return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? BigDecimal.ZERO : value.decimalValue();
    }

    private static JsonNode officer(JsonNode report, String officerId) {
        for (JsonNode node : report.path("byOfficer")) {
            if (officerId.equals(node.path("officerId").asText())) {
                return node;
            }
        }
        return com.fasterxml.jackson.databind.node.NullNode.getInstance();
    }

    @Test
    void exposureTracksReservesAndWorkload() throws Exception {
        String officer = "officer-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode before = exposure();
        BigDecimal usdReserveBefore = decimal(currency(before, "USD"), "totalReserve");

        UUID first = api.submit("POL-3001", "1000.00", "USD");
        UUID second = api.submit("POL-3001", "2000.00", "USD");
        api.assign(first, officer).andExpect(status().isOk());
        api.assign(second, officer).andExpect(status().isOk());
        api.assess(second, officer, "RECOMMEND_APPROVAL", "1500.00").andExpect(status().isCreated());

        JsonNode during = exposure();
        assertThat(during.path("openClaims").asLong()).isEqualTo(before.path("openClaims").asLong() + 2);
        assertThat(decimal(currency(during, "USD"), "totalReserve"))
                .isEqualByComparingTo(usdReserveBefore.add(new BigDecimal("2500.00")));
        JsonNode workload = officer(during, officer);
        assertThat(workload.path("openClaims").asLong()).isEqualTo(2);
        assertThat(workload.path("assigned").asLong()).isEqualTo(1);
        assertThat(workload.path("underReview").asLong()).isEqualTo(1);

        api.approve(second, "1200.00").andExpect(status().isOk());
        api.settle(second, "1200.00").andExpect(status().isOk());

        JsonNode after = exposure();
        assertThat(after.path("openClaims").asLong()).isEqualTo(before.path("openClaims").asLong() + 1);
        assertThat(decimal(currency(after, "USD"), "totalReserve"))
                .isEqualByComparingTo(usdReserveBefore.add(new BigDecimal("1000.00")));
        assertThat(officer(after, officer).path("openClaims").asLong()).isEqualTo(1);
    }

    @Test
    void unassignedClaimsAreCounted() throws Exception {
        long before = exposure().path("unassignedClaims").asLong();

        api.submit("100.00");

        assertThat(exposure().path("unassignedClaims").asLong()).isEqualTo(before + 1);
    }
}
