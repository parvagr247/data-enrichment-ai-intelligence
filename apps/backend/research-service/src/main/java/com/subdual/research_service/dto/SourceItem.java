package com.subdual.research_service.dto;

import java.time.Instant;

public record SourceItem(
        String url,
        Instant retrievedAt,
        String sourceType
) {}
