package com.subdual.research_service.integration.ai;

import java.util.List;
import java.util.Map;

import com.subdual.research_service.integration.ai.dto.AiExtractedFact;

public interface AiExtractionClient {
    Map<String, AiExtractedFact> extractFacts(
            String entityName,
            String entityType,
            String sourceUrl,
            String textContent,
            List<String> targetFields
    );
}
