package com.subdual.research_service.service;

import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.ResearchRequest;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic implementation of EntityNormalizer that sanitizes input URLs,
 * normalizes entity names, computes immutable SHA-256 IDs, and attaches metadata.
 */
@Component
public class DefaultEntityNormalizer implements EntityNormalizer {

    @Override
    public ResearchTarget normalize(ResearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ResearchRequest must not be null");
        }

        String rawUrl = request.url() != null ? request.url().trim() : "";
        String canonicalUrl = canonicalizeUrl(rawUrl);
        String entityId = computeEntityId(canonicalUrl);

        EntityType type = request.entityType() != null ? request.entityType() : EntityType.OTHER;

        String displayName = normalizeDisplayName(request.name(), canonicalUrl);
        Map<String, Object> metadata = request.metadata() != null ? Map.copyOf(request.metadata()) : Map.of();

        return new ResearchTarget(rawUrl, canonicalUrl, entityId, type, displayName, metadata);
    }

    public String canonicalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            int port = uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";

            String portPart = (port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
                    ? "" : ":" + port;

            return scheme + "://" + host + portPart + path + query;
        } catch (Exception e) {
            return rawUrl.trim();
        }
    }

    public String normalizeDisplayName(String rawName, String fallbackCanonicalUrl) {
        if (rawName == null || rawName.isBlank()) {
            return fallbackCanonicalUrl;
        }
        // Trim leading/trailing whitespace and collapse internal consecutive whitespace
        String normalized = rawName.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? fallbackCanonicalUrl : normalized;
    }

    public String computeEntityId(String canonicalUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
