package com.frauddetection.api.adapter.out.persistence.entity;

import com.frauddetection.api.domain.model.AlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_status_history")
public class AlertStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "previous_status")
    private AlertStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "new_status", nullable = false)
    private AlertStatus newStatus;

    @Column(name = "changed_by", length = 64)
    private String changedBy;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected AlertStatusHistoryEntity() {
        // JPA
    }

    public AlertStatusHistoryEntity(UUID alertId, AlertStatus previousStatus, AlertStatus newStatus,
                                    String changedBy, String comment, Instant changedAt) {
        this.alertId = alertId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.comment = comment;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public UUID getAlertId() { return alertId; }
    public AlertStatus getPreviousStatus() { return previousStatus; }
    public AlertStatus getNewStatus() { return newStatus; }
    public String getChangedBy() { return changedBy; }
    public String getComment() { return comment; }
    public Instant getChangedAt() { return changedAt; }
}
