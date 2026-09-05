package com.subdual.dataset_service.dto;

import java.util.List;
import java.util.Map;

public record EnrichmentJobRequest(
        String datasetName,
        String userRequirement,
        String defaultEntityType,
        Map<String, String> columnMapping,
        List<Map<String, String>> rows
) {
    public EnrichmentJobRequest {
        if (defaultEntityType == null || defaultEntityType.isBlank()) {
            defaultEntityType = "PERSON";
        }
        if (columnMapping == null) {
            columnMapping = Map.of();
        }
        if (rows == null) {
            rows = List.of();
        }
    }
}
