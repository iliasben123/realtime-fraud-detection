package com.frauddetection.api.domain.port.out;

import java.util.function.Supplier;

/**
 * Port sortant : exécution atomique d'un ensemble d'opérations de persistance.
 * Permet au cœur applicatif de délimiter une transaction sans dépendre de Spring.
 */
public interface UnitOfWork {

    <T> T execute(Supplier<T> work);
}
