package com.subdual.dataset_service.ingestion.model;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive dataset profiling report (Tasks 21-30).
 */
public record DatasetProfileReport(
        String datasetName,
        int totalRows,
        int validRows,
        int malformedRowCount,
        int duplicateRowCount,
        List<ColumnProfile> columns,
        List<String> detectedEntityColumns,
        List<String> detectedUrlColumns,
        List<String> detectedOrgColumns,
        List<String> existingEnrichedColumns,
        List<String> conflictingFields,
        double qualityScore,
        String qualityExplanation,
        Map<String, String> recommendedMapping,
        List<Map<String, String>> previewRows
) {
}
