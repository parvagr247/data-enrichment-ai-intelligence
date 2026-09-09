package com.subdual.research_service.integration.ai.dto;

import java.util.Map;

public record AiExtractionResponse(
        String entityName,
        String sourceUrl,
        Map<String, AiExtractedFact> facts,
        String modelUsed,
        long executionTimeMs
) {}
