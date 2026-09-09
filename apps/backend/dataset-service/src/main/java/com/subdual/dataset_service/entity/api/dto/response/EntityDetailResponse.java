package com.subdual.dataset_service.entity.api.dto.response;

import com.subdual.dataset_service.common.dto.EntityAttributeDto;
import com.subdual.dataset_service.common.dto.EntitySourceDto;

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
        Instant updatedAt,
        String executionStatus,
        String executionMessage,
        String priorityTier,
        Integer relevanceScore,
        String profileJson,
        String assessmentJson,
        String recommendationJson,
        String findingsJson
) {
    public EntityDetailResponse(
            String entityId,
            String displayName,
            String entityType,
            String canonicalUrl,
            List<EntitySourceDto> sources,
            Map<String, EntityAttributeDto> attributes,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(entityId, displayName, entityType, canonicalUrl, sources, attributes, createdAt, updatedAt,
                null, null, null, null, null, null, null, null);
    }
}
