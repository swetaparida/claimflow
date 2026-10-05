package com.insurer.claimflow.notification.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves contact details of a claim's claimant.
 */
public interface ClaimContactPort {

    Optional<String> claimantEmail(UUID claimId);
}
