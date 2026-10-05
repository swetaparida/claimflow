package com.insurer.claimflow.workmanagement.domain;

import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A claim-to-officer work allocation. At most one active assignment exists per claim;
 * re-assignment releases the previous one.
 */
public class Assignment {

    private final UUID id;
    private final UUID claimId;
    private final String officerId;
    private final String assignedBy;
    private final String note;
    private final Instant assignedAt;
    private Instant releasedAt;

    private Assignment(UUID id, UUID claimId, String officerId, String assignedBy, String note, Instant assignedAt,
                       Instant releasedAt) {
        this.id = Objects.requireNonNull(id);
        this.claimId = Objects.requireNonNull(claimId);
        this.officerId = Objects.requireNonNull(officerId);
        this.assignedBy = Objects.requireNonNull(assignedBy);
        this.note = note;
        this.assignedAt = Objects.requireNonNull(assignedAt);
        this.releasedAt = releasedAt;
    }

    public static Assignment create(UUID claimId, String officerId, String assignedBy, String note, Instant now) {
        return new Assignment(UUID.randomUUID(), claimId, officerId, assignedBy, note, now, null);
    }

    public static Assignment reconstitute(UUID id, UUID claimId, String officerId, String assignedBy, String note,
                                          Instant assignedAt, Instant releasedAt) {
        return new Assignment(id, claimId, officerId, assignedBy, note, assignedAt, releasedAt);
    }

    public void release(Instant now) {
        if (!isActive()) {
            throw new BusinessRuleViolationException("ASSIGNMENT_NOT_ACTIVE", "Assignment %s already released".formatted(id));
        }
        releasedAt = now;
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public String getOfficerId() {
        return officerId;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public String getNote() {
        return note;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
