package com.subdual.research_service.dto;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

/**
 * Normalized representation of a discovered research source.
 */
public record SourceItem(
        String url,
        String title,
        String snippet,
        String sourceType,
        String domain,
        String provider,
        Instant retrievedAt,
        Double relevance
) {
    public SourceItem(String url, String title, String sourceType, Instant retrievedAt, Double relevance) {
        this(url, title, null, sourceType, extractDomain(url), null, retrievedAt, relevance);
    }

    public SourceItem(String url, Instant retrievedAt, String sourceType) {
        this(url, null, null, sourceType, extractDomain(url), null, retrievedAt, 1.0);
    }

    public static String extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host != null) {
                return host.replaceFirst("^www\\.", "").toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
