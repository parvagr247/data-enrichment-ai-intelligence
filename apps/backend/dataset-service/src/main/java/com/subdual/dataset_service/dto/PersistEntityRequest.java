package com.subdual.dataset_service.dto;

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

        Map<String, EntityAttributeDto> attributes
) {
    public PersistEntityRequest {
        if (sources == null) sources = List.of();
        if (attributes == null) attributes = Map.of();
        if (entityType == null || entityType.isBlank()) entityType = "OTHER";
    }
}
