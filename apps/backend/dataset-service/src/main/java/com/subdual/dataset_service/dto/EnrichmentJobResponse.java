package com.subdual.dataset_service.dto;

import java.util.List;

public record EnrichmentJobResponse(
        String jobId,
        String datasetName,
        String status,
        String userRequirement,
        int totalRows,
        int completedRows,
        int failedRows,
        int progress,
        String createdAt,
        String completedAt,
        Long durationMs,
        List<RowEnrichmentResult> rowResults,
        String errorMessage
) {
    public EnrichmentJobResponse {
        if (rowResults == null) {
            rowResults = List.of();
        }
    }
}
