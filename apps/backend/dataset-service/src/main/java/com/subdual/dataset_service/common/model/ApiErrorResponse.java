package com.subdual.dataset_service.common.model;

import java.time.Instant;

/**
 * Standardized error response model across all microservices (Task 7).
 */
public record ApiErrorResponse(
        String code,
        String message,
        Object details,
        String timestamp,
        String requestId
) {
    public ApiErrorResponse(String code, String message, Object details, String requestId) {
        this(code, message, details, Instant.now().toString(), requestId);
    }

    public static ApiErrorResponse of(String code, String message, Object details, String requestId) {
        return new ApiErrorResponse(code, message, details, Instant.now().toString(), requestId);
    }
}
