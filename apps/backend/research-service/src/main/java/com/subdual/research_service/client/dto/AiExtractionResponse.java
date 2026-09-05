package com.subdual.research_service.client.dto;

import java.util.Map;

public record AiExtractionResponse(
        String entityName,
        String sourceUrl,
        Map<String, AiExtractedFact> facts,
        String modelUsed,
        long executionTimeMs
) {}
