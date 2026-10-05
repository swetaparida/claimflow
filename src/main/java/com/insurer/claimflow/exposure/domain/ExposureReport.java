package com.insurer.claimflow.exposure.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Point-in-time view of workload and financial exposure for claims managers.
 * Monetary totals are never summed across currencies.
 */
public record ExposureReport(
        Instant generatedAt,
        long openClaims,
        long unassignedClaims,
        List<CurrencyExposure> byCurrency,
        List<StatusBreakdown> byStatus,
        List<OfficerWorkload> byOfficer) {

    /**
     * @param totalReserve  outstanding case reserves on open claims (the financial exposure)
     * @param totalClaimed  amount claimed on open claims
     * @param totalApproved approved but not yet settled amounts
     * @param totalPaid     amounts paid on settled claims
     */
    public record CurrencyExposure(String currency, long openClaims, BigDecimal totalReserve, BigDecimal totalClaimed,
                                   BigDecimal totalApproved, BigDecimal totalPaid) {
    }

    public record StatusBreakdown(String status, long claimCount) {
    }

    public record OfficerWorkload(String officerId, long openClaims, long assigned, long underReview,
                                  long infoRequired, long approvedAwaitingSettlement) {
    }
}
