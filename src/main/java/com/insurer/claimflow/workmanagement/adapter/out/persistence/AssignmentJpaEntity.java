package com.insurer.claimflow.workmanagement.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assignment")
public class AssignmentJpaEntity {

    @Id
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "officer_id", nullable = false, length = 64)
    private String officerId;

    @Column(name = "assigned_by", nullable = false, length = 64)
    private String assignedBy;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected AssignmentJpaEntity() {
    }

    AssignmentJpaEntity(UUID id, UUID claimId, String officerId, String assignedBy, String note, Instant assignedAt) {
        this.id = id;
        this.claimId = claimId;
        this.officerId = officerId;
        this.assignedBy = assignedBy;
        this.note = note;
        this.assignedAt = assignedAt;
    }

    void applyRelease(Instant releasedAt) {
        this.releasedAt = releasedAt;
        this.active = releasedAt == null;
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

    public boolean isActive() {
        return active;
    }
}
