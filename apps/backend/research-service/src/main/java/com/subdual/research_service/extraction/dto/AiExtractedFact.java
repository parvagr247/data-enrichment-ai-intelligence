package com.subdual.research_service.extraction.dto;

public record AiExtractedFact(
        String value,
        String exactQuote,
        double confidenceScore
) {}
