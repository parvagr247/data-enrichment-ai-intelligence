package com.subdual.research_service.client.dto;

import java.time.Instant;

public record EntitySourceDto(
        String url,
        String title,
        String snippet,
        String sourceType,
        String domain,
        String provider,
        Double relevance,
        Instant retrievedAt
) {}
