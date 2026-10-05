package com.insurer.claimflow.lifecycle.adapter.out.persistence;

import com.insurer.claimflow.lifecycle.domain.IncidentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class IncidentJpaEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, unique = true)
    private ClaimJpaEntity claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", nullable = false, length = 32)
    private IncidentType type;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "location", length = 500)
    private String location;

    @Column(name = "description", nullable = false, length = 4000)
    private String description;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    protected IncidentJpaEntity() {
    }

    public IncidentJpaEntity(UUID id, ClaimJpaEntity claim, IncidentType type, LocalDate incidentDate,
                             String location, String description, Instant reportedAt) {
        this.id = id;
        this.claim = claim;
        this.type = type;
        this.incidentDate = incidentDate;
        this.location = location;
        this.description = description;
        this.reportedAt = reportedAt;
    }

    public UUID getId() {
        return id;
    }

    public IncidentType getType() {
        return type;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }
}
