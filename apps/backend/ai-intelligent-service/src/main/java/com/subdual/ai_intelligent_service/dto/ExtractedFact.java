package com.subdual.ai_intelligent_service.dto;

/**
 * A single empirical fact extracted from source text with verbatim citation.
 */
public record ExtractedFact(
        String value,
        String exactQuote,
        double confidenceScore
) {}
