package com.subdual.ai_intelligent_service.extraction.model;

public record ExtractedFact(
        String value,
        String exactQuote,
        double confidenceScore
) {}
