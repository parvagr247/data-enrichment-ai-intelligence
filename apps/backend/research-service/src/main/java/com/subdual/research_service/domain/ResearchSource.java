package com.subdual.research_service.domain;

import java.time.Instant;

public record ResearchSource(
        String url,
        String title,
        String sourceType,
        Instant retrievedAt,
        Double relevance
) {}
