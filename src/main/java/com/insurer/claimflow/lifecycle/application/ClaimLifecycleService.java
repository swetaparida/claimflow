package com.insurer.claimflow.lifecycle.application;

import com.insurer.claimflow.lifecycle.application.port.in.ClaimDecisionUseCase;
import com.insurer.claimflow.lifecycle.application.port.in.ClaimReviewUseCase;
import com.insurer.claimflow.lifecycle.domain.Claim;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ClaimLifecycleService implements ClaimDecisionUseCase, ClaimReviewUseCase {

    private final ClaimAggregateStore store;
    private final Clock clock;

    public ClaimLifecycleService(ClaimAggregateStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Claim beginReview(BeginReviewCommand command) {
        Claim claim = store.load(command.claimId());
        claim.beginReview(command.reviewerId(), clock.instant());
        return store.save(claim);
    }

    @Override
    @Transactional
    public Claim adjustReserve(AdjustReserveCommand command) {
        Claim claim = store.load(command.claimId());
        claim.adjustReserve(command.reserveAmount(), command.reason(), command.actor(), clock.instant());
        return store.save(claim);
    }

    @Override
    @Transactional
    public Claim approve(ApproveClaimCommand command) {
        Claim claim = store.load(command.claimId());
        claim.approve(command.approvedAmount(), command.notes(), command.decidedBy(), clock.instant());
        return store.save(claim);
    }

    @Override
    @Transactional
    public Claim reject(RejectClaimCommand command) {
        Claim claim = store.load(command.claimId());
        claim.reject(command.reason(), command.decidedBy(), clock.instant());
        return store.save(claim);
    }

    @Override
    @Transactional
    public Claim settle(SettleClaimCommand command) {
        Claim claim = store.load(command.claimId());
        claim.settle(command.settlementAmount(), command.paymentReference(), command.settledBy(), clock.instant());
        return store.save(claim);
    }
}
