package com.subdual.research_service.integration.ai;

public record AiExtractedFact(
        String value,
        String exactQuote,
        double confidenceScore
) {}
