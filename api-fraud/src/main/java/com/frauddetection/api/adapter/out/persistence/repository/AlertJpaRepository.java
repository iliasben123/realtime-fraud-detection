package com.frauddetection.api.adapter.out.persistence.repository;

import com.frauddetection.api.adapter.out.persistence.entity.AlertEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

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
}
