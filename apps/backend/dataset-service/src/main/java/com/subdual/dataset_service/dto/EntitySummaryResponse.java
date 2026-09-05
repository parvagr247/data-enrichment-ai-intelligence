package com.subdual.dataset_service.dto;

import java.time.Instant;

public record EntitySummaryResponse(
        String entityId,
        String displayName,
        String entityType,
        String canonicalUrl,
        int totalSources,
        int totalAttributes,
        Instant updatedAt
) {}
