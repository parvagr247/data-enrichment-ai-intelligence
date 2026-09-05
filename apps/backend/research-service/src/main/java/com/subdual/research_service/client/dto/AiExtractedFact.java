package com.subdual.research_service.client.dto;

public record AiExtractedFact(
        String value,
        String exactQuote,
        double confidenceScore
) {}
