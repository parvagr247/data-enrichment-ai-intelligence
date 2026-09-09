package com.subdual.dataset_service.dto.common;

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
