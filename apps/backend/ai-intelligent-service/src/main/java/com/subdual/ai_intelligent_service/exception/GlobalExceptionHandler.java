package com.subdual.ai_intelligent_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private static final String INSTANCE_PATH = "/api/v1/ai/extract";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detailMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed for extraction payload");

        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        String requestId = org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY);
        log.warn("[AI_HTTP_ERROR] Validation failure on {} [requestId={}]: {}", path, requestId, detailMessage);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detailMessage);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        problemDetail.setProperty("requestId", requestId);
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        problemDetail.setProperty("details", ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        String requestId = org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY);
        log.warn("[AI_HTTP_ERROR] Malformed JSON on {} [requestId={}]: {}", path, requestId, ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request payload");
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "MALFORMED_PAYLOAD");
        problemDetail.setProperty("requestId", requestId);
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        String requestId = org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY);
        log.warn("[AI_HTTP_ERROR] Illegal argument on {} [requestId={}]: {}", path, requestId, ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "BAD_REQUEST");
        problemDetail.setProperty("requestId", requestId);
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "/api/v1/ai";
        String requestId = org.slf4j.MDC.get(com.subdual.ai_intelligent_service.configuration.CorrelationIdFilter.MDC_KEY);
        log.error("[AI_HTTP_ERROR] Unhandled error on {} [requestId={}]: {}", path, requestId, ex.getMessage(), ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred: " + (ex.getMessage() != null ? ex.getMessage() : "Unknown error")
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setInstance(URI.create(path));
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");
        problemDetail.setProperty("requestId", requestId);
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(problemDetail);
    }
}
