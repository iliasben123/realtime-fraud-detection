package com.frauddetection.api.adapter.out.persistence.repository;

import com.frauddetection.api.adapter.out.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TransactionJpaRepository
        extends JpaRepository<TransactionEntity, UUID>, JpaSpecificationExecutor<TransactionEntity> {

    long countBySuspiciousTrue();

    long countByFraudTrue();

    long countBySuspiciousTrueAndFraudTrue();

    /**
     * Par version de modèle : nb scorées, nb fraudes labellisées, score ML moyen, score ML moyen
     * sur les fraudes, première et dernière date de traitement.
     */
    @Query("""
            select t.modelVersion,
                   count(t),
                   sum(case when t.fraud = true then 1 else 0 end),
                   avg(t.mlScore),
                   avg(case when t.fraud = true then t.mlScore else null end),
                   min(t.processedAt),
                   max(t.processedAt)
            from TransactionEntity t
            where t.modelVersion is not null
            group by t.modelVersion
            """)
    List<Object[]> statsByModelVersion();
}
