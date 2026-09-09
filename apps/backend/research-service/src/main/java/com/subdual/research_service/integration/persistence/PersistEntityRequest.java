package com.subdual.research_service.integration.persistence;

import java.util.List;
import java.util.Map;

public record PersistEntityRequest(
        String entityId,
        String displayName,
        String entityType,
        String canonicalUrl,
        List<EntitySourceDto> sources,
        Map<String, EntityAttributeDto> attributes
) {}
