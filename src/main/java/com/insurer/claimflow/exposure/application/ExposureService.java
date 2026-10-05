package com.insurer.claimflow.exposure.application;

import com.insurer.claimflow.exposure.application.port.in.ExposureQueryUseCase;
import com.insurer.claimflow.exposure.application.port.out.ExposureReadModelPort;
import com.insurer.claimflow.exposure.domain.ExposureReport;
import com.insurer.claimflow.exposure.domain.ExposureReport.CurrencyExposure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class ExposureService implements ExposureQueryUseCase {

    private final ExposureReadModelPort readModel;
    private final Clock clock;

    public ExposureService(ExposureReadModelPort readModel, Clock clock) {
        this.readModel = readModel;
        this.clock = clock;
    }

    /** REPEATABLE_READ gives all sections of the report the same snapshot. */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ExposureReport currentExposure() {
        List<CurrencyExposure> byCurrency = readModel.exposureByCurrency();
        long openClaims = byCurrency.stream().mapToLong(CurrencyExposure::openClaims).sum();
        return new ExposureReport(clock.instant(), openClaims, readModel.unassignedOpenClaims(), byCurrency,
                readModel.claimsByStatus(), readModel.workloadByOfficer());
    }
}
