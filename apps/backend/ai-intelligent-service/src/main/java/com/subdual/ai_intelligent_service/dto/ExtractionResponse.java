package com.subdual.ai_intelligent_service.dto;

import java.util.Map;

/**
 * Result of structured AI extraction containing grounded facts.
 */
public record ExtractionResponse(
        String entityName,
        String sourceUrl,
        Map<String, ExtractedFact> facts,
        String modelUsed,
        long executionTimeMs
) {}
