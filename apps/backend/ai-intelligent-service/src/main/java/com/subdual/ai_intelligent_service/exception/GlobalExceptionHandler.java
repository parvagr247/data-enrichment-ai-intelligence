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
    public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
        String detailMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed for extraction payload");

        log.warn("Validation failure on {}: {}", INSTANCE_PATH, detailMessage);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detailMessage);
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON on {}: {}", INSTANCE_PATH, ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed JSON request payload");
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Bad Request");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled error on {}: ", INSTANCE_PATH, ex);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred during AI extraction"
        );
        problemDetail.setType(DEFAULT_TYPE);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setInstance(URI.create(INSTANCE_PATH));
        return problemDetail;
    }
}
