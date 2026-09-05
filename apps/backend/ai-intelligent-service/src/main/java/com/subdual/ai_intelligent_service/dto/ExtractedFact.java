package com.subdual.ai_intelligent_service.dto;

public record ExtractedFact(
        String value,
        String exactQuote,
        double confidenceScore
) {}
