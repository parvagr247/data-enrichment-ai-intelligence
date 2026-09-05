package com.subdual.research_service.dto;

import com.subdual.research_service.domain.EntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record ResearchRequest(
        @NotBlank(message = "Field 'url' must be a valid, well-formed HTTP/HTTPS URL")
        @Pattern(regexp = "^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$", message = "Field 'url' must be a valid, well-formed HTTP/HTTPS URL")
        String url,

        EntityType entityType,

        String name,

        Map<String, Object> metadata
) {
    public ResearchRequest(String url, EntityType entityType, String name) {
        this(url, entityType, name, null);
    }

    public ResearchRequest {
        if (entityType == null) {
            entityType = EntityType.OTHER;
        }
    }
}
