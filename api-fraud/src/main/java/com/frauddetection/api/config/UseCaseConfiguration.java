package com.frauddetection.api.config;

import com.frauddetection.api.application.AlertService;
import com.frauddetection.api.application.StatisticsService;
import com.frauddetection.api.application.TransactionService;
import com.frauddetection.api.domain.port.out.AlertRepository;
import com.frauddetection.api.domain.port.out.AlertStatusHistoryRepository;
import com.frauddetection.api.domain.port.out.TransactionRepository;
import com.frauddetection.api.domain.port.out.UnitOfWork;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Câblage des cas d'utilisation : c'est ici, et seulement ici, que le cœur applicatif
 * (sans annotation Spring) est relié aux adaptateurs.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AlertService alertService(AlertRepository alerts, AlertStatusHistoryRepository history,
                              UnitOfWork unitOfWork, Clock clock) {
        return new AlertService(alerts, history, unitOfWork, clock);
    }

    @Bean
    TransactionService transactionService(TransactionRepository transactions, AlertRepository alerts) {
        return new TransactionService(transactions, alerts);
    }

    @Bean
    StatisticsService statisticsService(TransactionRepository transactions, AlertRepository alerts) {
        return new StatisticsService(transactions, alerts);
    }
}
