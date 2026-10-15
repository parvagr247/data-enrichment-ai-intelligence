package com.subdual.research_service.research.factory;

import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.dataset.DatasetPersistenceClient;
import com.subdual.research_service.integration.web.WebContentFetcher;
import com.subdual.research_service.research.discovery.provider.SearchProvider;

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
