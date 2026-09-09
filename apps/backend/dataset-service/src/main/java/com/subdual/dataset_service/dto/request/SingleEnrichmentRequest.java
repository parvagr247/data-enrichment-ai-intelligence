package com.subdual.dataset_service.dto.request;

import java.util.Map;

public record SingleEnrichmentRequest(
        Map<String, String> row,
        Map<String, String> columnMapping,
        String entityType,
        String userRequirement
) {
    public SingleEnrichmentRequest {
        if (row == null) {
            row = Map.of();
        }
        if (columnMapping == null) {
            columnMapping = Map.of();
        }
        if (entityType == null || entityType.isBlank()) {
            entityType = "PERSON";
        }
    }
}
