package com.subdual.research_service.domain;

import java.util.Map;

/**
 * Normalized internal entity target driving research and discovery.
 */
public record ResearchTarget(
        String rawUrl,
        String canonicalUrl,
        String entityId,
        EntityType entityType,
        String displayName,
        Map<String, Object> metadata
) {
    public ResearchTarget(String rawUrl, String canonicalUrl, String entityId, EntityType entityType, String displayName) {
        this(rawUrl, canonicalUrl, entityId, entityType, displayName, Map.of());
    }
}
