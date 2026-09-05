package com.subdual.research_service.api.dto;

import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchDepth;
import jakarta.validation.constraints.AssertTrue;

import java.util.List;
import java.util.Map;

public record ResearchRequest(
        String url,

        EntityType entityType,

        String name,

        String organization,

        String role,

        List<String> targetFields,

        ResearchDepth depth,

        Map<String, Object> metadata
) {
    public ResearchRequest(String url, EntityType entityType, String name, String organization, String role, List<String> targetFields, Map<String, Object> metadata) {
        this(url, entityType, name, organization, role, targetFields, ResearchDepth.NORMAL, metadata);
    }

    public ResearchRequest(String url, EntityType entityType, String name, Map<String, Object> metadata) {
        this(url, entityType, name, null, null, null, ResearchDepth.NORMAL, metadata);
    }

    public ResearchRequest(String url, EntityType entityType, String name) {
        this(url, entityType, name, null, null, null, ResearchDepth.NORMAL, null);
    }

    public ResearchRequest(String url) {
        this(url, EntityType.OTHER, null, null, null, null, ResearchDepth.NORMAL, null);
    }

    public ResearchRequest {
        if (entityType == null) {
            entityType = EntityType.OTHER;
        }
        if (targetFields == null) {
            targetFields = List.of();
        }
        if (depth == null) {
            depth = ResearchDepth.NORMAL;
        }
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    @AssertTrue(message = "Either 'url' or 'name' must be provided for research")
    public boolean hasTargetIdentifier() {
        return (url != null && !url.isBlank()) || (name != null && !name.isBlank());
    }

    @AssertTrue(message = "Field 'url' must be a valid, well-formed HTTP/HTTPS URL")
    public boolean isValidHttpUrl() {
        if (url == null) {
            return true;
        }
        if (url.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (Exception ex) {
            return false;
        }
    }
}
