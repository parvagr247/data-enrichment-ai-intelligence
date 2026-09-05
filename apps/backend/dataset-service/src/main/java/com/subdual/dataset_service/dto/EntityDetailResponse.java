package com.subdual.dataset_service.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EntityDetailResponse(
        String entityId,
        String displayName,
        String entityType,
        String canonicalUrl,
        List<EntitySourceDto> sources,
        Map<String, EntityAttributeDto> attributes,
        Instant createdAt,
        Instant updatedAt
) {}
