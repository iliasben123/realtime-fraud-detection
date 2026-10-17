package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.adapter.out.persistence.repository.AlertJpaRepository;
import com.frauddetection.api.adapter.out.persistence.repository.TransactionJpaRepository;
import com.frauddetection.api.domain.model.AlertStatus;
import com.frauddetection.api.domain.model.ModelVersionStatistics;
import com.frauddetection.api.domain.model.RuleFeedback;
import com.frauddetection.api.domain.port.out.ModelStatisticsRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class ModelStatisticsPersistenceAdapter implements ModelStatisticsRepository {

    static final String ML_RULE = "ML_HIGH_RISK";
    static final Set<AlertStatus> CONFIRMED = EnumSet.of(AlertStatus.CONFIRMED_FRAUD, AlertStatus.BLOCKED);
    static final Set<AlertStatus> PENDING = EnumSet.of(AlertStatus.OPEN, AlertStatus.UNDER_REVIEW);

    private final TransactionJpaRepository transactions;
    private final AlertJpaRepository alerts;

    ModelStatisticsPersistenceAdapter(TransactionJpaRepository transactions, AlertJpaRepository alerts) {
        this.transactions = transactions;
        this.alerts = alerts;
    }

    @Override
    public List<ModelVersionStatistics> byModelVersion() {
        // [version, mlAlerts, onFraud, confirmed, falsePositive, pending]
        Map<String, Object[]> mlByVersion = new HashMap<>();
        for (Object[] row : alerts.ruleStatsByModelVersion(ML_RULE, CONFIRMED, AlertStatus.FALSE_POSITIVE, PENDING)) {
            mlByVersion.put((String) row[0], row);
        }

        List<ModelVersionStatistics> result = new ArrayList<>();
        for (Object[] t : transactions.statsByModelVersion()) {
            String version = (String) t[0];
            Object[] ml = mlByVersion.getOrDefault(version, new Object[]{version, 0L, 0L, 0L, 0L, 0L});
            result.add(new ModelVersionStatistics(
                    version,
                    asLong(t[1]), asLong(t[2]),
                    asLong(ml[1]), asLong(ml[2]), asLong(ml[3]), asLong(ml[4]), asLong(ml[5]),
                    asDecimal(t[3]), asDecimal(t[4]),
                    (Instant) t[5], (Instant) t[6]));
        }
        return result;
    }

    @Override
    public List<RuleFeedback> ruleFeedback() {
        List<RuleFeedback> result = new ArrayList<>();
        for (Object[] r : alerts.feedbackByRule(CONFIRMED, AlertStatus.FALSE_POSITIVE, PENDING)) {
            result.add(new RuleFeedback((String) r[0], asLong(r[1]), asLong(r[2]), asLong(r[3]), asLong(r[4]), asLong(r[5])));
        }
        return result;
    }

    private static long asLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal asDecimal(Object o) {
        if (o == null) {
            return null;
        }
        BigDecimal value = o instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) o).doubleValue());
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
