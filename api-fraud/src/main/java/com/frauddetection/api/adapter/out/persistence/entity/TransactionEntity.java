package com.frauddetection.api.adapter.out.persistence.entity;

import com.frauddetection.api.domain.model.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionEntity {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private int step;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "name_orig", nullable = false, length = 32)
    private String nameOrig;

    @Column(name = "old_balance_orig", nullable = false, precision = 18, scale = 2)
    private BigDecimal oldBalanceOrig;

    @Column(name = "new_balance_orig", nullable = false, precision = 18, scale = 2)
    private BigDecimal newBalanceOrig;

    @Column(name = "name_dest", nullable = false, length = 32)
    private String nameDest;

    @Column(name = "old_balance_dest", nullable = false, precision = 18, scale = 2)
    private BigDecimal oldBalanceDest;

    @Column(name = "new_balance_dest", nullable = false, precision = 18, scale = 2)
    private BigDecimal newBalanceDest;

    @Column(name = "is_fraud", nullable = false)
    private boolean fraud;

    @Column(name = "is_flagged_fraud", nullable = false)
    private boolean flaggedFraud;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "is_suspicious", nullable = false)
    private boolean suspicious;

    protected TransactionEntity() {
        // JPA
    }

    public UUID getTransactionId() { return transactionId; }
    public int getStep() { return step; }
    public Instant getEventTime() { return eventTime; }
    public Instant getProcessedAt() { return processedAt; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getNameOrig() { return nameOrig; }
    public BigDecimal getOldBalanceOrig() { return oldBalanceOrig; }
    public BigDecimal getNewBalanceOrig() { return newBalanceOrig; }
    public String getNameDest() { return nameDest; }
    public BigDecimal getOldBalanceDest() { return oldBalanceDest; }
    public BigDecimal getNewBalanceDest() { return newBalanceDest; }
    public boolean isFraud() { return fraud; }
    public boolean isFlaggedFraud() { return flaggedFraud; }
    public BigDecimal getRiskScore() { return riskScore; }
    public boolean isSuspicious() { return suspicious; }
}
