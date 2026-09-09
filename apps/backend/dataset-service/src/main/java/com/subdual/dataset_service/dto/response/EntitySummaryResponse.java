package com.subdual.dataset_service.dto.response;

import java.time.Instant;

public record EntitySummaryResponse(
        String entityId,
        String displayName,
        String entityType,
        String canonicalUrl,
        int totalSources,
        int totalAttributes,
        Instant updatedAt,
        String priorityTier,
        Integer relevanceScore,
        String executionStatus
) {
    public EntitySummaryResponse(
            String entityId,
            String displayName,
            String entityType,
            String canonicalUrl,
            int totalSources,
            int totalAttributes,
            Instant updatedAt
    ) {
        this(entityId, displayName, entityType, canonicalUrl, totalSources, totalAttributes, updatedAt, null, null, null);
    }
}
