package com.insurer.claimflow.notification.adapter.out.claims;

import com.insurer.claimflow.lifecycle.application.port.in.ClaimQueryUseCase;
import com.insurer.claimflow.notification.application.port.out.ClaimContactPort;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves claimant contacts through the Lifecycle module's public query API (in-process call).
 */
@Component
class ClaimContactAdapter implements ClaimContactPort {

    private final ClaimQueryUseCase claims;

    ClaimContactAdapter(ClaimQueryUseCase claims) {
        this.claims = claims;
    }

    @Override
    public Optional<String> claimantEmail(UUID claimId) {
        try {
            return Optional.of(claims.getClaim(claimId).getClaimant().email());
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }
}
