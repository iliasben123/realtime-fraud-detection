package com.frauddetection.api.adapter.in.web;

import com.frauddetection.api.domain.exception.AlertNotFoundException;
import com.frauddetection.api.domain.exception.InsufficientPrivilegesException;
import com.frauddetection.api.domain.exception.InvalidStatusTransitionException;
import com.frauddetection.api.domain.exception.TransactionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduction des exceptions métier en réponses HTTP au format RFC 9457 (Problem Details). */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler({AlertNotFoundException.class, TransactionNotFoundException.class})
    ProblemDetail notFound(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Ressource introuvable");
        return pd;
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail invalidTransition(InvalidStatusTransitionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Transition de statut non autorisée");
        pd.setProperty("alertId", ex.getAlertId());
        pd.setProperty("currentStatus", ex.getFrom());
        pd.setProperty("requestedStatus", ex.getTo());
        pd.setProperty("allowedTransitions", ex.getFrom().allowedTransitions());
        return pd;
    }

    @ExceptionHandler(InsufficientPrivilegesException.class)
    ProblemDetail forbidden(InsufficientPrivilegesException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Habilitation insuffisante");
        pd.setProperty("username", ex.getUsername());
        pd.setProperty("requestedStatus", ex.getTarget());
        return pd;
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthorized(AuthenticationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
        pd.setTitle("Authentification échouée");
        return pd;
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail badRequest(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Requête invalide");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corps de requête invalide");
        pd.setTitle("Validation échouée");
        pd.setProperty("errors", errors);
        return pd;
    }
}
