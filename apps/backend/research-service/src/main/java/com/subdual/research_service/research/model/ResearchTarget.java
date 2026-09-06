package com.subdual.research_service.research.model;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record ResearchTarget(
        String rawUrl,
        String canonicalUrl,
        String entityId,
        EntityType entityType,
        String displayName,
        Map<String, Object> metadata
) {
    public ResearchTarget(String rawUrl, String canonicalUrl, String entityId, EntityType entityType, String displayName) {
        this(rawUrl, canonicalUrl, entityId, entityType, displayName, Map.of());
    }

    public String seedOrganization() {
        if (metadata != null && metadata.containsKey("organization")) {
            Object val = metadata.get("organization");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public String organization() {
        return seedOrganization();
    }

    public String seedRole() {
        if (metadata != null && metadata.containsKey("role")) {
            Object val = metadata.get("role");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public String role() {
        return seedRole();
    }

    public String firstName() {
        if (metadata != null && metadata.containsKey("firstName")) {
            Object val = metadata.get("firstName");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public String lastName() {
        if (metadata != null && metadata.containsKey("lastName")) {
            Object val = metadata.get("lastName");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public String fullName() {
        if (metadata != null && metadata.containsKey("fullName")) {
            Object val = metadata.get("fullName");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public String email() {
        if (metadata != null && metadata.containsKey("email")) {
            Object val = metadata.get("email");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public String location() {
        if (metadata != null && metadata.containsKey("location")) {
            Object val = metadata.get("location");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public List<String> targetFields() {
        if (metadata != null && metadata.containsKey("targetFields")) {
            Object obj = metadata.get("targetFields");
            if (obj instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }
        }
        return List.of();
    }

    public ResearchDepth depth() {
        if (metadata != null && metadata.containsKey("depth")) {
            Object obj = metadata.get("depth");
            if (obj instanceof ResearchDepth d) {
                return d;
            }
            if (obj instanceof String s) {
                try {
                    return ResearchDepth.valueOf(s.toUpperCase(Locale.ROOT));
                } catch (Exception ignored) {}
            }
        }
        return ResearchDepth.NORMAL;
    }

    public String userRequirement() {
        if (metadata != null && metadata.containsKey("userRequirement")) {
            Object val = metadata.get("userRequirement");
            return val != null ? val.toString().trim() : null;
        }
        return null;
    }

    public boolean isUrlAnchored() {
        return rawUrl != null && !rawUrl.isBlank() && (rawUrl.startsWith("http://") || rawUrl.startsWith("https://"));
    }

    public String primaryAnchorUrl() {
        return isUrlAnchored() ? (canonicalUrl != null ? canonicalUrl : rawUrl) : null;
    }

    public String domain() {
        String url = isUrlAnchored() ? primaryAnchorUrl() : null;
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
