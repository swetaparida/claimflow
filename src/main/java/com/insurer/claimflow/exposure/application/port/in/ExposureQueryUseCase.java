package com.insurer.claimflow.exposure.application.port.in;

import com.insurer.claimflow.exposure.domain.ExposureReport;

public interface ExposureQueryUseCase {

    ExposureReport currentExposure();
}
