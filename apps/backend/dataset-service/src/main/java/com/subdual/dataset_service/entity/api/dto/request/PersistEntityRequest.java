package com.subdual.dataset_service.entity.api.dto.request;

import com.subdual.dataset_service.common.dto.EntityAttributeDto;
import com.subdual.dataset_service.common.dto.EntitySourceDto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record PersistEntityRequest(
        @NotBlank(message = "Field 'entityId' must not be blank")
        String entityId,

        @NotBlank(message = "Field 'displayName' must not be blank")
        String displayName,

        String entityType,

        @NotBlank(message = "Field 'canonicalUrl' must not be blank")
        String canonicalUrl,

        List<EntitySourceDto> sources,

        Map<String, EntityAttributeDto> attributes,

        String executionStatus,

        String executionMessage,

        String priorityTier,

        Integer relevanceScore,

        String profileJson,

        String assessmentJson,

        String recommendationJson,

        String findingsJson
) {
    public PersistEntityRequest {
        if (sources == null) sources = List.of();
        if (attributes == null) attributes = Map.of();
        if (entityType == null || entityType.isBlank()) entityType = "OTHER";
    }

    public PersistEntityRequest(
            String entityId,
            String displayName,
            String entityType,
            String canonicalUrl,
            List<EntitySourceDto> sources,
            Map<String, EntityAttributeDto> attributes
    ) {
        this(entityId, displayName, entityType, canonicalUrl, sources, attributes,
                null, null, null, null, null, null, null, null);
    }
}
