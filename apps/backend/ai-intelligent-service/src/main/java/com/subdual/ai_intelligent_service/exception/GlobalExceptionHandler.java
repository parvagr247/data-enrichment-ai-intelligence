package com.subdual.ai_intelligent_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private static final String INSTANCE_PATH = "/api/v1/ai/extract";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest request) {
        String detailMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed for extraction payload");

        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        log.warn("Validation failure on {}: {}", path, detailMessage);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detailMessage);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        problemDetail.setProperty("details", ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList());
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex, jakarta.servlet.http.HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        log.warn("Malformed JSON on {}: {}", path, ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request payload");
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "MALFORMED_PAYLOAD");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex, jakarta.servlet.http.HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        log.warn("Illegal argument on {}: {}", path, ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "BAD_REQUEST");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        log.error("Unhandled error on {}: ", path, ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred"
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return problemDetail;
    }
}
