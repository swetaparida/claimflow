package com.insurer.claimflow.lifecycle.adapter.out.persistence;

import com.insurer.claimflow.lifecycle.domain.ClaimStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim")
public class ClaimJpaEntity {

    @Id
    private UUID id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 32)
    private String claimNumber;

    @Column(name = "policy_number", nullable = false, length = 32)
    private String policyNumber;

    @Column(name = "claimant_name", nullable = false, length = 200)
    private String claimantName;

    @Column(name = "claimant_email", nullable = false, length = 320)
    private String claimantEmail;

    @Column(name = "claimant_phone", length = 32)
    private String claimantPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClaimStatus status;

    @Column(name = "claimed_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "reserve_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal reserveAmount;

    @Column(name = "approved_amount", precision = 19, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "settled_amount", precision = 19, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "assigned_officer_id", length = 64)
    private String assignedOfficerId;

    @Column(name = "decision_reason", length = 2000)
    private String decisionReason;

    @Column(name = "payment_reference", length = 64)
    private String paymentReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToOne(mappedBy = "claim", cascade = CascadeType.ALL, optional = false)
    private IncidentJpaEntity incident;

    protected ClaimJpaEntity() {
    }

    public ClaimJpaEntity(UUID id, String claimNumber, String policyNumber, String claimantName,
                          String claimantEmail, String claimantPhone, BigDecimal claimedAmount, String currency,
                          Instant createdAt) {
        this.id = id;
        this.claimNumber = claimNumber;
        this.policyNumber = policyNumber;
        this.claimantName = claimantName;
        this.claimantEmail = claimantEmail;
        this.claimantPhone = claimantPhone;
        this.claimedAmount = claimedAmount;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    /** Copies the mutable part of the aggregate state onto this entity. */
    void applyMutableState(ClaimStatus status, String assignedOfficerId, BigDecimal reserveAmount,
                           BigDecimal approvedAmount, BigDecimal settledAmount, String paymentReference,
                           String decisionReason, Instant updatedAt) {
        this.status = status;
        this.assignedOfficerId = assignedOfficerId;
        this.reserveAmount = reserveAmount;
        this.approvedAmount = approvedAmount;
        this.settledAmount = settledAmount;
        this.paymentReference = paymentReference;
        this.decisionReason = decisionReason;
        this.updatedAt = updatedAt;
    }

    void attachIncident(IncidentJpaEntity incident) {
        this.incident = incident;
    }

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public String getClaimantEmail() {
        return claimantEmail;
    }

    public String getClaimantPhone() {
        return claimantPhone;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public BigDecimal getClaimedAmount() {
        return claimedAmount;
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

    public String getCurrency() {
        return currency;
    }

    public String getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public String getPaymentReference() {
        return paymentReference;
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

    public IncidentJpaEntity getIncident() {
        return incident;
    }
}
