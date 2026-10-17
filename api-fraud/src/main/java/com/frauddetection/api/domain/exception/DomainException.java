package com.frauddetection.api.domain.exception;

/** Racine des exceptions métier. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
