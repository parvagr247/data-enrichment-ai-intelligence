package com.subdual.research_service.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private static final String INSTANCE_PATH = "/api/v1/research";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        String detailMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed for request payload");

        log.warn("Validation failure on {}: {}", INSTANCE_PATH, detailMessage);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detailMessage);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        problemDetail.setProperty("details", ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList());
        return enrichProblemDetail(problemDetail, "VALIDATION_ERROR");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON or unreadable request body on {}: {}", INSTANCE_PATH, ex.getMessage());

        String detail = "Malformed request payload or invalid field value";
        if (ex.getMessage() != null && ex.getMessage().contains("EntityType")) {
            detail = "Invalid entityType: must be one of PERSON, ORGANIZATION, PRODUCT, REPOSITORY, WEBSITE, OTHER";
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return enrichProblemDetail(problemDetail, "MALFORMED_PAYLOAD");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument on {}: {}", INSTANCE_PATH, ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return enrichProblemDetail(problemDetail, "BAD_REQUEST");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRuleException(BusinessRuleException ex) {
        log.warn("Business rule violation on {}: {}", INSTANCE_PATH, ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return enrichProblemDetail(problemDetail, "BUSINESS_RULE_VIOLATION");
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalServiceException(ExternalServiceException ex) {
        log.error("External service failure on {}: {}", INSTANCE_PATH, ex.getMessage());

        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if (ex.getCause() instanceof TimeoutException
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"))) {
            status = HttpStatus.GATEWAY_TIMEOUT;
        }

        String sanitizedMessage = sanitizeDetail(ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, sanitizedMessage);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle(status == HttpStatus.GATEWAY_TIMEOUT ? "Gateway Timeout" : "Bad Gateway");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return enrichProblemDetail(problemDetail, status == HttpStatus.GATEWAY_TIMEOUT ? "GATEWAY_TIMEOUT" : "EXTERNAL_SERVICE_ERROR");
    }

    private String sanitizeDetail(String rawMessage) {
        if (rawMessage == null) {
            return "An external service error occurred";
        }
        return rawMessage.replaceAll("(?i)(api[_-]?key|secret|token|password|auth)=[^&\\s]+", "$1=***");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.debug("Resource not found on {}: {}", ex.getResourcePath(), ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setInstance(URI.create("/" + ex.getResourcePath()));
        return enrichProblemDetail(problemDetail, "RESOURCE_NOT_FOUND");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled server exception on {}: ", INSTANCE_PATH, ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred"
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return enrichProblemDetail(problemDetail, "INTERNAL_SERVER_ERROR");
    }

    private ProblemDetail enrichProblemDetail(ProblemDetail pd, String code) {
        pd.setProperty("code", code);
        pd.setProperty("requestId", org.slf4j.MDC.get(com.subdual.research_service.common.filter.CorrelationIdFilter.MDC_KEY));
        pd.setProperty("timestamp", java.time.Instant.now().toString());
        return pd;
    }
}
