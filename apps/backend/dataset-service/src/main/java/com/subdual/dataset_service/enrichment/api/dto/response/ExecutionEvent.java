package com.subdual.dataset_service.enrichment.api.dto.response;

import java.util.Map;

public record ExecutionEvent(
        String jobId,
        String rowId,
        int rowIndex,
        String entity,
        String status,
        String stage,
        String workerId,
        String message,
        String timestamp,
        Map<String, Object> metadata
) {
    public ExecutionEvent {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
