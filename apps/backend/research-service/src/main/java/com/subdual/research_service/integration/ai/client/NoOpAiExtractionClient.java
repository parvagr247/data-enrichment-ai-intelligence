package com.subdual.research_service.integration.ai.client;

import com.subdual.research_service.integration.ai.dto.AiExtractedFact;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Null Object implementation of AiExtractionClient.
 * Provides a clean fallback when AI extraction is unconfigured or in offline/test modes.
 */
public class NoOpAiExtractionClient implements AiExtractionClient {

    @Override // Returns an empty facts map for no-op AI extraction.
    public Map<String, AiExtractedFact> extractFacts(
            String entityName,
            String entityType,
            String sourceUrl,
            String textContent,
            List<String> targetFields
    ) {
        return Collections.emptyMap();
    }
}
