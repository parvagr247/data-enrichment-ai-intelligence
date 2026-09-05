package com.subdual.research_service.domain;

import java.time.Instant;

public record DiscoveredSource(
        String url,
        String title,
        String sourceType,
        Instant retrievedAt,
        Double relevance
) {}
