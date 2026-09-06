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

        Map<String, Object> metadata,

        String userRequirement,

        String firstName,

        String lastName,

        String fullName,

        String email,

        String location
) {
    public ResearchRequest(String url, EntityType entityType, String name, String organization, String role, List<String> targetFields, ResearchDepth depth, Map<String, Object> metadata, String userRequirement) {
        this(url, entityType, name, organization, role, targetFields, depth, metadata, userRequirement, null, null, null, null, null);
    }

    public ResearchRequest(String url, EntityType entityType, String name, String organization, String role, List<String> targetFields, ResearchDepth depth, Map<String, Object> metadata) {
        this(url, entityType, name, organization, role, targetFields, depth, metadata, null, null, null, null, null, null);
    }

    public ResearchRequest(String url, EntityType entityType, String name, String organization, String role, List<String> targetFields, Map<String, Object> metadata) {
        this(url, entityType, name, organization, role, targetFields, ResearchDepth.NORMAL, metadata, null, null, null, null, null, null);
    }

    public ResearchRequest(String url, EntityType entityType, String name, Map<String, Object> metadata) {
        this(url, entityType, name, null, null, null, ResearchDepth.NORMAL, metadata, null, null, null, null, null, null);
    }

    public ResearchRequest(String url, EntityType entityType, String name) {
        this(url, entityType, name, null, null, null, ResearchDepth.NORMAL, null, null, null, null, null, null, null);
    }

    public ResearchRequest(String url) {
        this(url, EntityType.OTHER, null, null, null, null, ResearchDepth.NORMAL, null, null, null, null, null, null, null);
    }

    public ResearchRequest {
        if (url != null && !url.isBlank()) {
            url = com.subdual.research_service.util.UrlNormalizer.unwrapLink(url);
        }
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

    @AssertTrue(message = "Either 'url', 'name', 'fullName', or 'firstName' must be provided for research")
    public boolean hasTargetIdentifier() {
        return (url != null && !url.isBlank())
                || (name != null && !name.isBlank())
                || (fullName != null && !fullName.isBlank())
                || (firstName != null && !firstName.isBlank());
    }

    @AssertTrue(message = "Field 'url' must be a valid, well-formed HTTP/HTTPS URL")
    public boolean isValidHttpUrl() {
        if (url == null) {
            return true;
        }
        String cleanUrl = com.subdual.research_service.util.UrlNormalizer.unwrapLink(url);
        if (cleanUrl.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(cleanUrl.trim());
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
