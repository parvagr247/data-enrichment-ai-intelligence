package com.subdual.dataset_service.dto;

import java.util.List;
import java.util.Map;

public record RowEnrichmentResult(
        String rowId,
        int rowIndex,
        Map<String, String> originalData,
        String status,
        String displayName,
        String canonicalUrl,
        String entityType,
        Map<String, EntityAttributeDto> attributes,
        List<String> unresolvedFields,
        List<String> conflicts,
        double confidence,
        List<EntitySourceDto> sources,
        String errorMessage
) {
    public RowEnrichmentResult {
        if (originalData == null) {
            originalData = Map.of();
        }
        if (attributes == null) {
            attributes = Map.of();
        }
        if (unresolvedFields == null) {
            unresolvedFields = List.of();
        }
        if (conflicts == null) {
            conflicts = List.of();
        }
        if (sources == null) {
            sources = List.of();
        }
    }
}
