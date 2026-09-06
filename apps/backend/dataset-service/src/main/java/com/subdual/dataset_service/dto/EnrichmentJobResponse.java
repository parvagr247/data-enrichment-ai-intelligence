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
        String errorMessage,
        Integer concurrency,
        Integer degradedRows,
        Integer partialRows
) {
    public EnrichmentJobResponse {
        if (rowResults == null) {
            rowResults = List.of();
        }
        if (concurrency == null) {
            concurrency = 3;
        }
        if (degradedRows == null) {
            degradedRows = 0;
        }
        if (partialRows == null) {
            partialRows = 0;
        }
    }

    public EnrichmentJobResponse(
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
            String errorMessage,
            Integer concurrency
    ) {
        this(jobId, datasetName, status, userRequirement, totalRows, completedRows, failedRows,
                progress, createdAt, completedAt, durationMs, rowResults, errorMessage, concurrency, 0, 0);
    }

    public EnrichmentJobResponse(
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
        this(jobId, datasetName, status, userRequirement, totalRows, completedRows, failedRows,
                progress, createdAt, completedAt, durationMs, rowResults, errorMessage, 3, 0, 0);
    }
}
