package com.subdual.ai_intelligent_service.extraction.api.dto;

import com.subdual.ai_intelligent_service.extraction.model.ExtractedFact;

import java.util.Map;

public record ExtractionResponse(
        String entityName,
        String sourceUrl,
        Map<String, ExtractedFact> facts,
        String modelUsed,
        long executionTimeMs
) {}
