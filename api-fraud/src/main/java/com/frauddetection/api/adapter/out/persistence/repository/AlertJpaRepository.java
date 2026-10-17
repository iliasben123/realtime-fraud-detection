package com.frauddetection.api.adapter.out.persistence.repository;

import com.frauddetection.api.adapter.out.persistence.entity.AlertEntity;
import com.frauddetection.api.domain.model.AlertStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertJpaRepository
        extends JpaRepository<AlertEntity, UUID>, JpaSpecificationExecutor<AlertEntity> {

    @EntityGraph(attributePaths = "transaction")
    Optional<AlertEntity> findWithTransactionByAlertId(UUID alertId);

    List<AlertEntity> findByTransactionTransactionIdOrderByCreatedAtAsc(UUID transactionId);

    @Query("select a.status, count(a) from AlertEntity a group by a.status")
    List<Object[]> countGroupedByStatus();

    @Query("select a.severity, count(a) from AlertEntity a group by a.severity")
    List<Object[]> countGroupedBySeverity();

    @Query("select a.ruleName, count(a) from AlertEntity a group by a.ruleName order by count(a) desc")
    List<Object[]> countGroupedByRule();

    /**
     * Alertes d'une règle (ex. ML_HIGH_RISK) par version de modèle : total, sur fraude labellisée,
     * confirmées par un analyste (statuts `confirmed`), classées faux positif, en attente (`pending`).
     */
    @Query("""
            select t.modelVersion,
                   count(a),
                   sum(case when t.fraud = true then 1 else 0 end),
                   sum(case when a.status in :confirmed then 1 else 0 end),
                   sum(case when a.status = :falsePositive then 1 else 0 end),
                   sum(case when a.status in :pending then 1 else 0 end)
            from AlertEntity a join a.transaction t
            where a.ruleName = :ruleName and t.modelVersion is not null
            group by t.modelVersion
            """)
    List<Object[]> ruleStatsByModelVersion(@Param("ruleName") String ruleName,
                                           @Param("confirmed") Collection<AlertStatus> confirmed,
                                           @Param("falsePositive") AlertStatus falsePositive,
                                           @Param("pending") Collection<AlertStatus> pending);

    /** Même décomposition, par règle, toutes versions confondues. */
    @Query("""
            select a.ruleName,
                   count(a),
                   sum(case when t.fraud = true then 1 else 0 end),
                   sum(case when a.status in :confirmed then 1 else 0 end),
                   sum(case when a.status = :falsePositive then 1 else 0 end),
                   sum(case when a.status in :pending then 1 else 0 end)
            from AlertEntity a join a.transaction t
            group by a.ruleName
            """)
    List<Object[]> feedbackByRule(@Param("confirmed") Collection<AlertStatus> confirmed,
                                  @Param("falsePositive") AlertStatus falsePositive,
                                  @Param("pending") Collection<AlertStatus> pending);
}
