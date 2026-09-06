package com.subdual.dataset_service.ingestion.model;

import java.util.List;

/**
 * Profiling metrics for a single column in an ingested dataset (Tasks 21, 22).
 */
public record ColumnProfile(
        String columnName,
        ColumnRole detectedRole,
        double completenessPercentage,
        int populatedCount,
        int totalCount,
        List<String> sampleValues
) {
}
