package com.frauddetection.api.adapter.out.persistence.repository;

import com.frauddetection.api.adapter.out.persistence.entity.AlertStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertStatusHistoryJpaRepository extends JpaRepository<AlertStatusHistoryEntity, Long> {

    List<AlertStatusHistoryEntity> findByAlertIdOrderByChangedAtAscIdAsc(UUID alertId);
}
