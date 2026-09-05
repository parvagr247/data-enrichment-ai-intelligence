package com.subdual.research_service.persistence.dto;

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
