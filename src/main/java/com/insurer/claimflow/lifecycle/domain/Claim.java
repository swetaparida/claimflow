package com.insurer.claimflow.lifecycle.domain;

import com.insurer.claimflow.lifecycle.domain.event.ClaimAssigned;
import com.insurer.claimflow.lifecycle.domain.event.ClaimCreated;
import com.insurer.claimflow.lifecycle.domain.event.ClaimDomainEvent;
import com.insurer.claimflow.lifecycle.domain.event.ClaimStatusChanged;
import com.insurer.claimflow.lifecycle.domain.event.DecisionMade;
import com.insurer.claimflow.lifecycle.domain.event.ReserveChanged;
import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import com.insurer.claimflow.shared.domain.InvalidStateTransitionException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Claim aggregate root. Enforces the lifecycle and financial invariants and records a domain event for
 * every meaningful change. It is pure Java – no persistence or framework concerns.
 */
public class Claim {

    private static final Set<ClaimStatus> ASSIGNABLE =
            EnumSet.of(ClaimStatus.REPORTED, ClaimStatus.ASSIGNED, ClaimStatus.UNDER_REVIEW, ClaimStatus.INFO_REQUIRED);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final UUID id;
    private final String claimNumber;
    private final String policyNumber;
    private final Claimant claimant;
    private final Incident incident;
    private final BigDecimal claimedAmount;
    private final String currency;
    private final Instant createdAt;
    private final Long version;

    private ClaimStatus status;
    private String assignedOfficerId;
    private BigDecimal reserveAmount;
    private BigDecimal approvedAmount;
    private BigDecimal settledAmount;
    private String paymentReference;
    private String decisionReason;
    private Instant updatedAt;

    private final List<ClaimDomainEvent> pendingEvents = new ArrayList<>();

    private Claim(UUID id, String claimNumber, String policyNumber, Claimant claimant, Incident incident,
                  BigDecimal claimedAmount, String currency, ClaimStatus status, String assignedOfficerId,
                  BigDecimal reserveAmount, BigDecimal approvedAmount, BigDecimal settledAmount,
                  String paymentReference, String decisionReason, Instant createdAt, Instant updatedAt, Long version) {
        this.id = Objects.requireNonNull(id);
        this.claimNumber = Objects.requireNonNull(claimNumber);
        this.policyNumber = Objects.requireNonNull(policyNumber);
        this.claimant = Objects.requireNonNull(claimant);
        this.incident = Objects.requireNonNull(incident);
        this.claimedAmount = Objects.requireNonNull(claimedAmount);
        this.currency = Objects.requireNonNull(currency);
        this.status = Objects.requireNonNull(status);
        this.assignedOfficerId = assignedOfficerId;
        this.reserveAmount = reserveAmount == null ? ZERO : reserveAmount;
        this.approvedAmount = approvedAmount;
        this.settledAmount = settledAmount;
        this.paymentReference = paymentReference;
        this.decisionReason = decisionReason;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    /**
     * First notification of loss. The claim starts as {@link ClaimStatus#REPORTED} with an initial case
     * reserve equal to the claimed amount.
     */
    public static Claim submit(UUID id, String claimNumber, String policyNumber, Claimant claimant, Incident incident,
                               BigDecimal claimedAmount, String currency, String actor, Instant now) {
        BigDecimal amount = money(claimedAmount);
        if (amount.signum() <= 0) {
            throw new BusinessRuleViolationException("INVALID_CLAIMED_AMOUNT", "Claimed amount must be positive");
        }
        Claim claim = new Claim(id, claimNumber, policyNumber, claimant, incident, amount, currency.toUpperCase(),
                ClaimStatus.REPORTED, null, ZERO, null, null, null, null, now, now, null);
        claim.register(new ClaimCreated(UUID.randomUUID(), id, claimNumber, policyNumber, claimant.name(),
                claimant.email(), incident.type(), incident.date(), amount, claim.currency, actor, now));
        claim.changeReserve(amount, "INITIAL_RESERVE", actor, now);
        return claim;
    }

    /** Rebuilds an aggregate from persisted state. Emits no events. */
    public static Claim reconstitute(UUID id, String claimNumber, String policyNumber, Claimant claimant,
                                     Incident incident, BigDecimal claimedAmount, String currency, ClaimStatus status,
                                     String assignedOfficerId, BigDecimal reserveAmount, BigDecimal approvedAmount,
                                     BigDecimal settledAmount, String paymentReference, String decisionReason,
                                     Instant createdAt, Instant updatedAt, Long version) {
        return new Claim(id, claimNumber, policyNumber, claimant, incident, claimedAmount, currency, status,
                assignedOfficerId, reserveAmount, approvedAmount, settledAmount, paymentReference, decisionReason,
                createdAt, updatedAt, version);
    }

    // ---------------------------------------------------------------- behaviour

    /**
     * Assigns (or re-assigns) the claim to a claims officer. The first assignment moves the claim from
     * REPORTED to ASSIGNED; re-assignment keeps the current status.
     */
    public void assignTo(String officerId, String actor, Instant now) {
        requireText(officerId, "officerId");
        if (!ASSIGNABLE.contains(status)) {
            throw new InvalidStateTransitionException("Claim", id, status.name(), ClaimStatus.ASSIGNED.name());
        }
        if (officerId.equals(assignedOfficerId)) {
            throw new BusinessRuleViolationException("ALREADY_ASSIGNED",
                    "Claim %s is already assigned to %s".formatted(claimNumber, officerId));
        }
        String previous = assignedOfficerId;
        assignedOfficerId = officerId;
        updatedAt = now;
        register(new ClaimAssigned(UUID.randomUUID(), id, claimNumber, officerId, previous, actor, now));
        if (status == ClaimStatus.REPORTED) {
            transitionTo(ClaimStatus.ASSIGNED, "Assigned to " + officerId, actor, now);
        }
    }

    /**
     * Puts the claim under review (from ASSIGNED, or back from INFO_REQUIRED). No-op if already under review.
     * Enforces "claim must be assigned before review".
     */
    public void beginReview(String actor, Instant now) {
        if (status == ClaimStatus.UNDER_REVIEW) {
            return;
        }
        transitionTo(ClaimStatus.UNDER_REVIEW, "Assessment in progress", actor, now);
    }

    public void requestInformation(String reason, String actor, Instant now) {
        transitionTo(ClaimStatus.INFO_REQUIRED, reason, actor, now);
    }

    /** Sets the case reserve (expected cost). Only allowed while the claim is open. */
    public void adjustReserve(BigDecimal newReserve, String reason, String actor, Instant now) {
        if (!ClaimStatus.OPEN.contains(status)) {
            throw new BusinessRuleViolationException("CLAIM_CLOSED",
                    "Reserve cannot be changed on a %s claim".formatted(status));
        }
        BigDecimal amount = money(newReserve);
        if (amount.signum() < 0) {
            throw new BusinessRuleViolationException("INVALID_RESERVE", "Reserve must not be negative");
        }
        changeReserve(amount, reason, actor, now);
    }

    /** Enforces "review required before approval". */
    public void approve(BigDecimal amount, String notes, String actor, Instant now) {
        ensureCanTransitionTo(ClaimStatus.APPROVED);
        BigDecimal approved = money(amount);
        if (approved.signum() <= 0) {
            throw new BusinessRuleViolationException("INVALID_APPROVED_AMOUNT", "Approved amount must be positive");
        }
        if (approved.compareTo(claimedAmount) > 0) {
            throw new BusinessRuleViolationException("APPROVED_AMOUNT_EXCEEDS_CLAIM",
                    "Approved amount %s exceeds claimed amount %s".formatted(approved, claimedAmount));
        }
        approvedAmount = approved;
        decisionReason = notes;
        transitionTo(ClaimStatus.APPROVED, notes, actor, now);
        register(new DecisionMade(UUID.randomUUID(), id, claimNumber, Decision.APPROVED, approved, currency, notes,
                actor, now));
        changeReserve(approved, "APPROVAL", actor, now);
    }

    /** Enforces "review required before rejection". */
    public void reject(String reason, String actor, Instant now) {
        ensureCanTransitionTo(ClaimStatus.REJECTED);
        requireText(reason, "reason");
        decisionReason = reason;
        transitionTo(ClaimStatus.REJECTED, reason, actor, now);
        register(new DecisionMade(UUID.randomUUID(), id, claimNumber, Decision.REJECTED, null, currency, reason,
                actor, now));
        changeReserve(ZERO, "REJECTION", actor, now);
    }

    /** Enforces "approval required before settlement". */
    public void settle(BigDecimal amount, String paymentRef, String actor, Instant now) {
        ensureCanTransitionTo(ClaimStatus.SETTLED);
        requireText(paymentRef, "paymentReference");
        BigDecimal settled = money(amount);
        if (settled.signum() <= 0) {
            throw new BusinessRuleViolationException("INVALID_SETTLEMENT_AMOUNT", "Settlement amount must be positive");
        }
        if (settled.compareTo(approvedAmount) > 0) {
            throw new BusinessRuleViolationException("SETTLEMENT_EXCEEDS_APPROVED_AMOUNT",
                    "Settlement amount %s exceeds approved amount %s".formatted(settled, approvedAmount));
        }
        settledAmount = settled;
        paymentReference = paymentRef;
        transitionTo(ClaimStatus.SETTLED, "Paid with reference " + paymentRef, actor, now);
        changeReserve(ZERO, "SETTLEMENT", actor, now);
    }

    /** Returns and clears the events recorded since the aggregate was loaded. */
    public List<ClaimDomainEvent> pullDomainEvents() {
        List<ClaimDomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public List<ClaimDomainEvent> peekDomainEvents() {
        return List.copyOf(pendingEvents);
    }

    // ---------------------------------------------------------------- internals

    private void transitionTo(ClaimStatus target, String reason, String actor, Instant now) {
        ensureCanTransitionTo(target);
        ClaimStatus from = status;
        status = target;
        updatedAt = now;
        register(new ClaimStatusChanged(UUID.randomUUID(), id, claimNumber, from, target, reason, actor, now));
    }

    private void ensureCanTransitionTo(ClaimStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Claim", id, status.name(), target.name());
        }
    }

    private void changeReserve(BigDecimal newReserve, String reason, String actor, Instant now) {
        if (newReserve.compareTo(reserveAmount) == 0) {
            return;
        }
        BigDecimal previous = reserveAmount;
        reserveAmount = newReserve;
        updatedAt = now;
        register(new ReserveChanged(UUID.randomUUID(), id, claimNumber, previous, newReserve, currency, reason,
                actor, now));
    }

    private void register(ClaimDomainEvent event) {
        pendingEvents.add(event);
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new BusinessRuleViolationException("AMOUNT_REQUIRED", "Amount is required");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("FIELD_REQUIRED", field + " is required");
        }
    }

    // ---------------------------------------------------------------- accessors

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public Claimant getClaimant() {
        return claimant;
    }

    public Incident getIncident() {
        return incident;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public BigDecimal getReserveAmount() {
        return reserveAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public BigDecimal getSettledAmount() {
        return settledAmount;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
