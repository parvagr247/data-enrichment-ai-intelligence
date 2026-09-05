package com.subdual.research_service.api.dto;

import com.subdual.research_service.research.model.EntityType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record ResearchRequest(
        @Pattern(regexp = "^(?i)(https?://[^\\s]+)?$", message = "Field 'url' must be a valid, well-formed HTTP/HTTPS URL")
        String url,

        EntityType entityType,

        String name,

        Map<String, Object> metadata
) {
    public ResearchRequest(String url, EntityType entityType, String name) {
        this(url, entityType, name, null);
    }

    public ResearchRequest(String url) {
        this(url, EntityType.OTHER, null, null);
    }

    public ResearchRequest {
        if (entityType == null) {
            entityType = EntityType.OTHER;
        }
    }

    @AssertTrue(message = "Either 'url' or 'name' must be provided for research")
    public boolean hasTargetIdentifier() {
        return (url != null && !url.isBlank()) || (name != null && !name.isBlank());
    }
}
