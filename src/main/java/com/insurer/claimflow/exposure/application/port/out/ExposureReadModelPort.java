package com.insurer.claimflow.exposure.application.port.out;

import com.insurer.claimflow.exposure.domain.ExposureReport.CurrencyExposure;
import com.insurer.claimflow.exposure.domain.ExposureReport.OfficerWorkload;
import com.insurer.claimflow.exposure.domain.ExposureReport.StatusBreakdown;

import java.util.List;

/**
 * Read-only projection over claim data used for management reporting.
 */
public interface ExposureReadModelPort {

    List<CurrencyExposure> exposureByCurrency();

    List<StatusBreakdown> claimsByStatus();

    List<OfficerWorkload> workloadByOfficer();

    long unassignedOpenClaims();
}
