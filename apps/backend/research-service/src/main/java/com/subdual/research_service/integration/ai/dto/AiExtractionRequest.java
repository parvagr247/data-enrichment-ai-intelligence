package com.subdual.research_service.integration.ai.dto;

import java.util.List;

public record AiExtractionRequest(
        String entityName,
        String entityType,
        String sourceUrl,
        String textContent,
        List<String> targetFields
) {}
