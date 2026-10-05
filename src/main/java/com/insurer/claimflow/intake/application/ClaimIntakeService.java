package com.insurer.claimflow.intake.application;

import com.insurer.claimflow.intake.application.port.in.SubmitClaimUseCase;
import com.insurer.claimflow.lifecycle.application.ClaimAggregateStore;
import com.insurer.claimflow.lifecycle.application.port.out.ClaimNumberGeneratorPort;
import com.insurer.claimflow.lifecycle.domain.Claim;
import com.insurer.claimflow.lifecycle.domain.Claimant;
import com.insurer.claimflow.lifecycle.domain.Incident;
import com.insurer.claimflow.policy.application.port.in.PolicyQueryUseCase;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * First notification of loss: validates coverage against the Policy Reference module and registers the claim.
 */
@Service
public class ClaimIntakeService implements SubmitClaimUseCase {

    private final PolicyQueryUseCase policies;
    private final ClaimNumberGeneratorPort claimNumbers;
    private final ClaimAggregateStore store;
    private final Clock clock;

    public ClaimIntakeService(PolicyQueryUseCase policies, ClaimNumberGeneratorPort claimNumbers,
                              ClaimAggregateStore store, Clock clock) {
        this.policies = policies;
        this.claimNumbers = claimNumbers;
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Claim submit(SubmitClaimCommand cmd) {
        Instant now = clock.instant();
        if (cmd.incidentDate().isAfter(LocalDate.ofInstant(now, ZoneOffset.UTC))) {
            throw new BusinessRuleViolationException("INCIDENT_DATE_IN_FUTURE", "Incident date cannot be in the future");
        }
        policies.verifyCoverage(cmd.policyNumber(), cmd.incidentDate(), cmd.claimedAmount(), cmd.currency());

        Claim claim = Claim.submit(
                UUID.randomUUID(),
                claimNumbers.nextClaimNumber(),
                cmd.policyNumber(),
                new Claimant(cmd.claimantName(), cmd.claimantEmail(), cmd.claimantPhone()),
                new Incident(UUID.randomUUID(), cmd.incidentType(), cmd.incidentDate(), cmd.incidentLocation(),
                        cmd.incidentDescription()),
                cmd.claimedAmount(),
                cmd.currency(),
                cmd.submittedBy() == null ? "claimant" : cmd.submittedBy(),
                now);
        return store.save(claim);
    }
}
