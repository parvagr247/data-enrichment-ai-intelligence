package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.api.dto.ResearchRequest;
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
import java.util.Set;

/**
 * Deterministic implementation of EntityNormalizer that sanitizes input URLs,
 * normalizes entity names, computes immutable SHA-256 IDs, and attaches metadata.
 */
@Component
public class DefaultEntityNormalizer implements EntityNormalizer {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref_src", "fbclid", "gclid", "mc_eid", "_ga", "_gl"
    );

    @Override
    public ResearchTarget normalize(ResearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("ResearchRequest must not be null");
        }

        String rawUrl = request.url() != null ? request.url().trim() : "";
        EntityType type = request.entityType() != null ? request.entityType() : EntityType.OTHER;
        String displayName = normalizeDisplayName(request.name(), rawUrl);

        String canonicalUrl;
        if (!rawUrl.isBlank()) {
            canonicalUrl = canonicalizeUrl(rawUrl);
        } else {
            String slug = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            canonicalUrl = "urn:entity:" + type.name().toLowerCase(Locale.ROOT) + ":" + (slug.isEmpty() ? "unnamed" : slug);
        }

        String entityId = computeEntityId(canonicalUrl);
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
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            String query = "";
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                String cleanParams = java.util.Arrays.stream(uri.getQuery().split("&"))
                        .filter(param -> !param.isBlank())
                        .filter(param -> {
                            String key = param.split("=")[0].toLowerCase(Locale.ROOT);
                            return !TRACKING_PARAMS.contains(key);
                        })
                        .sorted()
                        .collect(java.util.stream.Collectors.joining("&"));
                if (!cleanParams.isBlank()) {
                    query = "?" + cleanParams;
                }
            }

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
