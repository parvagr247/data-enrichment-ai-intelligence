package com.subdual.dataset_service.common.model;

import java.time.Instant;

/**
 * Standardized response metadata (Task 8).
 */
public record ResponseMetadata(
        String requestId,
        String jobId,
        String status,
        String timestamp
) {
    public ResponseMetadata(String requestId, String jobId, String status) {
        this(requestId, jobId, status, Instant.now().toString());
    }

    public static ResponseMetadata of(String requestId, String jobId, String status) {
        return new ResponseMetadata(requestId, jobId, status, Instant.now().toString());
    }
}
