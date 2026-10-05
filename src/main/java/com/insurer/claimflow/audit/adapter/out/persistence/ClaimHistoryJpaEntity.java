package com.insurer.claimflow.audit.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "claim_history")
public class ClaimHistoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", length = 32)
    private String toStatus;

    @Column(name = "actor", length = 64)
    private String actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ClaimHistoryJpaEntity() {
    }

    public ClaimHistoryJpaEntity(UUID id, UUID eventId, UUID claimId, String eventType, String fromStatus,
                                 String toStatus, String actor, String details, Instant occurredAt) {
        this.id = id;
        this.eventId = eventId;
        this.claimId = claimId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getClaimId() {
        return claimId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public String getDetails() {
        return details;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
