package com.subdual.research_service.extraction.dto;

import java.util.List;

public record AiExtractionRequest(
        String entityName,
        String entityType,
        String sourceUrl,
        String textContent,
        List<String> targetFields
) {}
