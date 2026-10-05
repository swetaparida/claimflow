package com.insurer.claimflow.shared.web;

import com.insurer.claimflow.shared.domain.BusinessRuleViolationException;
import com.insurer.claimflow.shared.domain.InvalidStateTransitionException;
import com.insurer.claimflow.shared.domain.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Translates exceptions into RFC 7807 problem details with a stable machine-readable {@code code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://api.claimflow.example/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidStateTransitionException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Invalid state transition", ex.getCode(), ex.getMessage());
        pd.setProperty("currentStatus", ex.getCurrentState());
        pd.setProperty("targetStatus", ex.getTargetState());
        return pd;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRule(BusinessRuleViolationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violation", ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", "CONCURRENT_MODIFICATION",
                "The resource was modified by another request. Reload and retry.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "Data conflict", "DATA_INTEGRITY_VIOLATION",
                "The request conflicts with the current state of the data.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED", "Request is invalid");
        pd.setProperty("errors", ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "INTERNAL_ERROR",
                "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_FAILED", "Request is invalid");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create(TYPE_BASE + code.toLowerCase().replace('_', '-')));
        pd.setProperty("code", code);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
