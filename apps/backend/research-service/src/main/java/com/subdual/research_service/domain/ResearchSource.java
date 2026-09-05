package com.subdual.research_service.domain;

import java.time.Instant;

public record ResearchSource(
        String url,
        String title,
        String sourceType,
        Instant retrievedAt,
        Double relevance,
        Double qualityScore,
        String snippet
) {
    public ResearchSource(String url, String title, String sourceType, Instant retrievedAt, Double relevance, Double qualityScore) {
        this(url, title, sourceType, retrievedAt, relevance, qualityScore, null);
    }

    public ResearchSource(String url, String title, String sourceType, Instant retrievedAt, Double relevance) {
        this(url, title, sourceType, retrievedAt, relevance, relevance, null);
    }
}
