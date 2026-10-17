package com.frauddetection.api.adapter.out.persistence.repository;

import com.frauddetection.api.adapter.out.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface TransactionJpaRepository
        extends JpaRepository<TransactionEntity, UUID>, JpaSpecificationExecutor<TransactionEntity> {

    long countBySuspiciousTrue();

    long countByFraudTrue();

    long countBySuspiciousTrueAndFraudTrue();
}
