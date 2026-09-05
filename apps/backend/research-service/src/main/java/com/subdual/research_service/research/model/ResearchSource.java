package com.subdual.research_service.research.model;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;

public record ResearchSource(
        String url,
        String title,
        String sourceType,
        Instant retrievedAt,
        Double relevance,
        Double qualityScore,
        String snippet,
        String domain,
        String provider
) {
    public ResearchSource(String url, String title, String sourceType, Instant retrievedAt, Double relevance, Double qualityScore, String snippet) {
        this(url, title, sourceType, retrievedAt, relevance, qualityScore, snippet, extractDomain(url), null);
    }

    public ResearchSource(String url, String title, String sourceType, Instant retrievedAt, Double relevance, Double qualityScore) {
        this(url, title, sourceType, retrievedAt, relevance, qualityScore, null, extractDomain(url), null);
    }

    public ResearchSource(String url, String title, String sourceType, Instant retrievedAt, Double relevance) {
        this(url, title, sourceType, retrievedAt, relevance, relevance, null, extractDomain(url), null);
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
