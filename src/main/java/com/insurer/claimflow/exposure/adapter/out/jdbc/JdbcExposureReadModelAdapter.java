package com.insurer.claimflow.exposure.adapter.out.jdbc;

import com.insurer.claimflow.exposure.application.port.out.ExposureReadModelPort;
import com.insurer.claimflow.exposure.domain.ExposureReport.CurrencyExposure;
import com.insurer.claimflow.exposure.domain.ExposureReport.OfficerWorkload;
import com.insurer.claimflow.exposure.domain.ExposureReport.StatusBreakdown;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aggregates directly in SQL so the report scales with the number of claims, not with the JVM heap.
 */
@Component
class JdbcExposureReadModelAdapter implements ExposureReadModelPort {

    private static final String OPEN = "('REPORTED','ASSIGNED','UNDER_REVIEW','INFO_REQUIRED','APPROVED')";

    private final JdbcTemplate jdbc;

    JdbcExposureReadModelAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CurrencyExposure> exposureByCurrency() {
        return jdbc.query("""
                SELECT currency,
                       COUNT(*) FILTER (WHERE status IN %1$s)                              AS open_claims,
                       COALESCE(SUM(reserve_amount) FILTER (WHERE status IN %1$s), 0)      AS total_reserve,
                       COALESCE(SUM(claimed_amount) FILTER (WHERE status IN %1$s), 0)      AS total_claimed,
                       COALESCE(SUM(approved_amount) FILTER (WHERE status = 'APPROVED'), 0) AS total_approved,
                       COALESCE(SUM(settled_amount) FILTER (WHERE status = 'SETTLED'), 0)  AS total_paid
                FROM claim
                GROUP BY currency
                ORDER BY currency
                """.formatted(OPEN),
                (rs, i) -> new CurrencyExposure(rs.getString("currency"), rs.getLong("open_claims"),
                        rs.getBigDecimal("total_reserve"), rs.getBigDecimal("total_claimed"),
                        rs.getBigDecimal("total_approved"), rs.getBigDecimal("total_paid")));
    }

    @Override
    public List<StatusBreakdown> claimsByStatus() {
        return jdbc.query("SELECT status, COUNT(*) AS cnt FROM claim GROUP BY status ORDER BY status",
                (rs, i) -> new StatusBreakdown(rs.getString("status"), rs.getLong("cnt")));
    }

    @Override
    public List<OfficerWorkload> workloadByOfficer() {
        return jdbc.query("""
                SELECT assigned_officer_id,
                       COUNT(*)                                          AS open_claims,
                       COUNT(*) FILTER (WHERE status = 'ASSIGNED')       AS assigned,
                       COUNT(*) FILTER (WHERE status = 'UNDER_REVIEW')   AS under_review,
                       COUNT(*) FILTER (WHERE status = 'INFO_REQUIRED')  AS info_required,
                       COUNT(*) FILTER (WHERE status = 'APPROVED')       AS approved
                FROM claim
                WHERE assigned_officer_id IS NOT NULL AND status IN %s
                GROUP BY assigned_officer_id
                ORDER BY open_claims DESC, assigned_officer_id
                """.formatted(OPEN),
                (rs, i) -> new OfficerWorkload(rs.getString("assigned_officer_id"), rs.getLong("open_claims"),
                        rs.getLong("assigned"), rs.getLong("under_review"), rs.getLong("info_required"),
                        rs.getLong("approved")));
    }

    @Override
    public long unassignedOpenClaims() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claim WHERE assigned_officer_id IS NULL AND status IN " + OPEN, Long.class);
        return count == null ? 0 : count;
    }
}
