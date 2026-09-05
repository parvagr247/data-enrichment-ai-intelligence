package com.subdual.research_service.exception;

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
        return problemDetail;
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
        return problemDetail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument on {}: {}", INSTANCE_PATH, ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return problemDetail;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRuleException(BusinessRuleException ex) {
        log.warn("Business rule violation on {}: {}", INSTANCE_PATH, ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return problemDetail;
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ProblemDetail handleExternalServiceException(ExternalServiceException ex) {
        log.error("External service failure on {}: {}", INSTANCE_PATH, ex.getMessage());

        HttpStatus status = HttpStatus.BAD_GATEWAY;
        if (ex.getCause() instanceof java.util.concurrent.TimeoutException
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("timeout"))) {
            status = HttpStatus.GATEWAY_TIMEOUT;
        }

        String sanitizedMessage = sanitizeDetail(ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, sanitizedMessage);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle(status == HttpStatus.GATEWAY_TIMEOUT ? "Gateway Timeout" : "Bad Gateway");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return problemDetail;
    }

    private String sanitizeDetail(String rawMessage) {
        if (rawMessage == null) {
            return "An external service error occurred";
        }
        return rawMessage.replaceAll("(?i)(api[_-]?key|secret|token|password|auth)=[^&\\s]+", "$1=***");
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
        return problemDetail;
    }
}
