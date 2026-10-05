package com.insurer.claimflow.workmanagement.application.port.in;

import com.insurer.claimflow.workmanagement.domain.Assignment;

import java.util.List;
import java.util.UUID;

public interface AssignClaimUseCase {

    Assignment assign(AssignClaimCommand command);

    List<Assignment> assignmentsOf(UUID claimId);

    record AssignClaimCommand(UUID claimId, String officerId, String assignedBy, String note) {
    }
}
