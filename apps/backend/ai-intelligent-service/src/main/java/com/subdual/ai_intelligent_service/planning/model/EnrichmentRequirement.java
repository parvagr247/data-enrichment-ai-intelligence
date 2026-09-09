package com.subdual.ai_intelligent_service.planning.model;

import java.util.List;

/**
 * User-provided or system enrichment requirement input.
 */
public record EnrichmentRequirement(
        String userObjective,
        String entityType,
        List<String> existingDatasetColumns,
        List<String> explicitFields
) {
    public EnrichmentRequirement(String userObjective, String entityType) {
        this(userObjective, entityType, List.of(), List.of());
    }

    public boolean isBlankRequirement() {
        return userObjective == null || userObjective.isBlank();
    }
}
