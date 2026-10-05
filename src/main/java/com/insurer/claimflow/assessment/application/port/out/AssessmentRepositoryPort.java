package com.insurer.claimflow.assessment.application.port.out;

import com.insurer.claimflow.assessment.domain.Assessment;

import java.util.List;
import java.util.UUID;

public interface AssessmentRepositoryPort {

    void save(Assessment assessment);

    List<Assessment> findByClaimId(UUID claimId);
}
