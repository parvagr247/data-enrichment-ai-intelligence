package com.subdual.dataset_service.dataset.api.dto.response;

import com.subdual.dataset_service.dataset.model.ColumnProfile;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive dataset profiling report.
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
