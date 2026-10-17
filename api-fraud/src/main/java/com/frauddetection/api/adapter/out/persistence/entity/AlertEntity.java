package com.frauddetection.api.adapter.out.persistence.entity;

import com.frauddetection.api.domain.model.AlertSeverity;
import com.frauddetection.api.domain.model.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
public class AlertEntity {

    @Id
    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private TransactionEntity transaction;

    @Column(name = "rule_name", nullable = false, length = 64)
    private String ruleName;

    @Column(columnDefinition = "text")
    private String description;

    // Types énumérés natifs PostgreSQL (fraud.alert_severity / fraud.alert_status)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Alimentée par le trigger PostgreSQL trg_alerts_updated_at : jamais écrite par Hibernate,
    // relue après chaque INSERT / UPDATE (RETURNING) pour que le domaine reflète la base.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "text")
    private String reviewComment;

    protected AlertEntity() {
        // JPA
    }

    public AlertEntity(UUID alertId, TransactionEntity transaction, String ruleName, String description,
                       AlertSeverity severity, BigDecimal riskScore, AlertStatus status,
                       Instant createdAt, Instant updatedAt, String reviewedBy, String reviewComment) {
        this.alertId = alertId;
        this.transaction = transaction;
        this.ruleName = ruleName;
        this.description = description;
        this.severity = severity;
        this.riskScore = riskScore;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.reviewedBy = reviewedBy;
        this.reviewComment = reviewComment;
    }

    /** Applique l'état d'une alerte métier mise à jour (seuls les champs modifiables bougent). */
    public void applyReview(AlertStatus status, String reviewedBy, String reviewComment) {
        this.status = status;
        this.reviewedBy = reviewedBy;
        this.reviewComment = reviewComment;
    }

    public UUID getAlertId() { return alertId; }
    public TransactionEntity getTransaction() { return transaction; }
    public String getRuleName() { return ruleName; }
    public String getDescription() { return description; }
    public AlertSeverity getSeverity() { return severity; }
    public BigDecimal getRiskScore() { return riskScore; }
    public AlertStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewComment() { return reviewComment; }
}
