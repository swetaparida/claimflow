package com.insurer.claimflow.lifecycle.domain.event;

import com.insurer.claimflow.shared.domain.DomainEvent;

import java.util.UUID;

/**
 * All events emitted by the {@link com.insurer.claimflow.lifecycle.domain.Claim} aggregate.
 * Sealed so that event routing (topic mapping, auditing) is checked exhaustively by the compiler.
 */
public sealed interface ClaimDomainEvent extends DomainEvent
        permits ClaimCreated, ClaimAssigned, ClaimStatusChanged, ReserveChanged, DecisionMade {

    UUID claimId();

    String claimNumber();

    String actor();
}
