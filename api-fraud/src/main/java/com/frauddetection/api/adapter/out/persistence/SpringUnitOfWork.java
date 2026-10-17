package com.frauddetection.api.adapter.out.persistence;

import com.frauddetection.api.domain.port.out.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Implémentation du port UnitOfWork via les transactions Spring. */
@Component
class SpringUnitOfWork implements UnitOfWork {

    private final TransactionTemplate template;

    SpringUnitOfWork(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
