package com.insurer.claimflow.assessment.application.port.in;

import com.insurer.claimflow.assessment.domain.Assessment;
import com.insurer.claimflow.assessment.domain.AssessmentOutcome;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RecordAssessmentUseCase {

    Assessment record(RecordAssessmentCommand command);

    List<Assessment> assessmentsOf(UUID claimId);

    record RecordAssessmentCommand(UUID claimId, String assessorId, AssessmentOutcome outcome, String findings,
                                   BigDecimal recommendedReserve) {
    }
}
