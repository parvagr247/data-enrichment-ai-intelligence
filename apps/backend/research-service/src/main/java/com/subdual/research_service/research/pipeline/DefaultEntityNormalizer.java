package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

@Component
public class DefaultEntityNormalizer implements EntityNormalizer {

    @Override // Normalizes raw research request into canonical research target.
    public ResearchTarget normalize(ResearchRequest request) {
        if (request == null) throw new IllegalArgumentException("ResearchRequest must not be null");
        
        String rawUrl = request.url() != null ? request.url().trim() : "";
        EntityType type = resolveEntityType(request.entityType(), rawUrl);
        String displayName = resolveCompositeDisplayName(request, rawUrl);

        String canonicalUrl = resolveCanonicalUrl(rawUrl, type, displayName, request.organization());
        String entityId = computeEntityId(canonicalUrl);
        Map<String, Object> metadata = buildTargetMetadata(request, displayName);

        return new ResearchTarget(rawUrl, canonicalUrl, entityId, type, displayName, metadata);
    }

    private EntityType resolveEntityType(EntityType type, String rawUrl) {
        if (type != null && type != EntityType.OTHER) {
            return type;
        }
        if (rawUrl == null || rawUrl.isBlank()) {
            return EntityType.OTHER;
        }
        String lowerUrl = rawUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.contains("linkedin.com/in/")) {
            return EntityType.PERSON;
        }
        if (lowerUrl.contains("linkedin.com/company/")) {
            return EntityType.ORGANIZATION;
        }
        if (lowerUrl.contains("github.com/")) {
            String path = lowerUrl.replaceFirst("^https?://(www\\.)?github\\.com/", "").replaceAll("/+$", "");
            return path.contains("/") ? EntityType.REPOSITORY : EntityType.ORGANIZATION;
        }
        return EntityType.OTHER;
    }

    public String resolveCompositeDisplayName(ResearchRequest request, String fallbackUrl) {
        if (request == null) return (fallbackUrl != null && !fallbackUrl.isBlank()) ? fallbackUrl : "unnamed";

        String fullName = sanitizeToken(request.fullName());
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }

        String firstName = sanitizeToken(request.firstName());
        String lastName = sanitizeToken(request.lastName());
        if (firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank()) {
            return firstName + " " + lastName;
        }
        if (firstName != null && !firstName.isBlank()) {
            return firstName;
        }
        if (lastName != null && !lastName.isBlank()) {
            return lastName;
        }

        String legacyName = sanitizeToken(request.name());
        if (legacyName != null && !legacyName.isBlank()) {
            return legacyName;
        }

        return (fallbackUrl != null && !fallbackUrl.isBlank()) ? fallbackUrl : "unnamed";
    }

    private String sanitizeToken(String token) {
        if (token == null) return null;
        String s = token.trim();
        if (s.equalsIgnoreCase("null") || s.equalsIgnoreCase("undefined")) {
            return null;
        }
        s = s.replaceAll("\\s+", " ").trim();
        return s.isBlank() ? null : s;
    }

    private Map<String, Object> buildTargetMetadata(ResearchRequest request, String displayName) {
        java.util.Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        if (request.firstName() != null && !request.firstName().isBlank()) {
            metadata.put("firstName", request.firstName().trim());
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            metadata.put("lastName", request.lastName().trim());
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            metadata.put("fullName", request.fullName().trim());
        } else if (displayName != null && !displayName.isBlank() && !displayName.startsWith("http")) {
            metadata.put("fullName", displayName);
        }
        if (request.email() != null && !request.email().isBlank()) {
            metadata.put("email", request.email().trim());
        }
        if (request.location() != null && !request.location().isBlank()) {
            metadata.put("location", request.location().trim());
        }
        if (request.organization() != null && !request.organization().isBlank()) {
            metadata.put("organization", request.organization().trim());
        }
        if (request.role() != null && !request.role().isBlank()) {
            metadata.put("role", request.role().trim());
        }
        if (request.targetFields() != null && !request.targetFields().isEmpty()) {
            metadata.put("targetFields", request.targetFields());
        }
        if (request.depth() != null) {
            metadata.put("depth", request.depth());
        }
        if (request.userRequirement() != null && !request.userRequirement().isBlank()) {
            metadata.put("userRequirement", request.userRequirement().trim());
        }
        return Map.copyOf(metadata);
    }

    private String resolveCanonicalUrl(String rawUrl, EntityType type, String displayName, String organization) {
        if (!rawUrl.isBlank()) return canonicalizeUrl(rawUrl);
        
        String slug = generateUrnSlug(displayName);
        String orgSlug = (organization != null && !organization.isBlank()) ? ":" + generateUrnSlug(organization) : "";
        return "urn:entity:" + type.name().toLowerCase(Locale.ROOT) + ":" + (slug.isEmpty() ? "unnamed" : slug) + orgSlug;
    }

    private String generateUrnSlug(String displayName) {
        return displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public String canonicalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return "";
        String unwrapped = com.subdual.research_service.util.UrlNormalizer.unwrapLink(rawUrl);
        if (unwrapped.isBlank()) return "";
        try {
            int hashIdx = unwrapped.indexOf('#');
            if (hashIdx >= 0) {
                unwrapped = unwrapped.substring(0, hashIdx);
            }
            unwrapped = unwrapped.replace(" ", "%20");
            if (!unwrapped.contains("://")) {
                unwrapped = "https://" + unwrapped;
            }
            URI uri = URI.create(unwrapped);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            if (com.subdual.research_service.util.UrlNormalizer.isLinkedInInternational(host)) {
                host = "linkedin.com";
            }
            String portPart = (uri.getPort() == -1 || ("http".equals(scheme) && uri.getPort() == 80) || ("https".equals(scheme) && uri.getPort() == 443)) ? "" : ":" + uri.getPort();
            String path = com.subdual.research_service.util.UrlNormalizer.normalizePath(uri.getRawPath());
            String query = com.subdual.research_service.util.UrlNormalizer.cleanQueryParameters(uri.getRawQuery());

            return scheme + "://" + host + portPart + path + query;
        } catch (Exception e) {
            return unwrapped.trim();
        }
    }

    public String normalizeDisplayName(String rawName, String fallbackCanonicalUrl) {
        if (rawName == null || rawName.isBlank()) {
            return fallbackCanonicalUrl;
        }
        return rawName.trim().replaceAll("\\s+", " ");
    }

    public String computeEntityId(String canonicalUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }
    }
}
