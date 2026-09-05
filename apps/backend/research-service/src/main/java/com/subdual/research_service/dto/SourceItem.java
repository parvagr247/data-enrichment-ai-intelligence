package com.subdual.research_service.dto;

import java.time.Instant;

public record SourceItem(
        String url,
        String title,
        String sourceType,
        Instant retrievedAt,
        Double relevance
) {
    public SourceItem(String url, Instant retrievedAt, String sourceType) {
        this(url, null, sourceType, retrievedAt, 1.0);
    }
}
