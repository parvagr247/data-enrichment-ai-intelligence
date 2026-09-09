package com.subdual.research_service.integration.ai.client;

import com.subdual.research_service.integration.ai.dto.AiExtractedFact;


import java.util.List;
import java.util.Map;

public interface AiExtractionClient {
    Map<String, AiExtractedFact> extractFacts(
            String entityName,
            String entityType,
            String sourceUrl,
            String textContent,
            List<String> targetFields
    );
}
