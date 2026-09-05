package com.subdual.research_service.service;

import java.time.Instant;

public record ExtractedDocument(
        String url,
        String title,
        String metaDescription,
        String siteName,
        String cleanText,
        Instant extractedAt
) {}
