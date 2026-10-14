package com.subdual.research_service.factory;

import com.subdual.research_service.discovery.provider.SearchProvider;
import com.subdual.research_service.integration.ai.client.AiExtractionClient;
import com.subdual.research_service.integration.persistence.client.DatasetPersistenceClient;
import com.subdual.research_service.integration.web.WebContentFetcher;

/**
 * Abstract factory declaring factory methods for creating cohesive families
 * of research infrastructure components (search, AI extraction, persistence, web fetching).
 */
public interface ResearchComponentFactory {
    SearchProvider createSearchProvider();
    AiExtractionClient createAiExtractionClient();
    DatasetPersistenceClient createDatasetPersistenceClient();
    WebContentFetcher createWebContentFetcher();
}
