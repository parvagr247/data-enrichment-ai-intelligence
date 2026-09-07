package com.subdual.dataset_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private static final String INSTANCE_PATH = "/api/v1/entities";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        String detailMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed for entity payload");

        log.warn("Validation failure on {}: {}", INSTANCE_PATH, detailMessage);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detailMessage);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.dataset_service.common.filter.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        problemDetail.setProperty("details", ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList());
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument on {}: {}", INSTANCE_PATH, ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        problemDetail.setProperty("code", "BAD_REQUEST");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.dataset_service.common.filter.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return problemDetail;
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Uploaded file exceeds maximum allowed size: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded file exceeds the maximum allowed file size limit (50MB)."
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Payload Too Large");
        problemDetail.setInstance(URI.create("/api/v2/datasets/upload"));
        problemDetail.setProperty("code", "MAX_UPLOAD_SIZE_EXCEEDED");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.dataset_service.common.filter.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled error on {}: ", INSTANCE_PATH, ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred during persistence"
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        problemDetail.setProperty("code", "INTERNAL_SERVER_ERROR");
        problemDetail.setProperty("requestId", org.slf4j.MDC.get(com.subdual.dataset_service.common.filter.CorrelationIdFilter.MDC_KEY));
        problemDetail.setProperty("timestamp", java.time.Instant.now().toString());
        return problemDetail;
    }
}
