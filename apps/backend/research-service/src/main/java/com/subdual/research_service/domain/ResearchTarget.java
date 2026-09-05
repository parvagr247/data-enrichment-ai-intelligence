package com.subdual.research_service.domain;

public record ResearchTarget(
        String rawUrl,
        String canonicalUrl,
        String entityId,
        EntityType entityType,
        String displayName
) {}
