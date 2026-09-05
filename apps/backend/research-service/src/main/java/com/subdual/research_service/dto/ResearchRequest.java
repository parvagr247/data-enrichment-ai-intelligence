package com.subdual.research_service.dto;

import com.subdual.research_service.domain.EntityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResearchRequest(
        @NotBlank(message = "Field 'url' must be a valid, well-formed HTTP/HTTPS URL")
        @Pattern(regexp = "^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$", message = "Field 'url' must be a valid, well-formed HTTP/HTTPS URL")
        String url,

        EntityType entityType,

        String name
) {
    public ResearchRequest {
        if (entityType == null) {
            entityType = EntityType.OTHER;
        }
    }
}
