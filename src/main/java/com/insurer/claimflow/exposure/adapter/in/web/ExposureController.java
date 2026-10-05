package com.insurer.claimflow.exposure.adapter.in.web;

import com.insurer.claimflow.exposure.application.port.in.ExposureQueryUseCase;
import com.insurer.claimflow.exposure.domain.ExposureReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Exposure")
class ExposureController {

    private final ExposureQueryUseCase exposure;

    ExposureController(ExposureQueryUseCase exposure) {
        this.exposure = exposure;
    }

    @GetMapping("/api/v1/exposure")
    @Operation(summary = "Current workload and financial exposure",
            description = "Open-claim counts, outstanding reserves per currency, status breakdown and officer workload.")
    ExposureReport currentExposure() {
        return exposure.currentExposure();
    }
}
