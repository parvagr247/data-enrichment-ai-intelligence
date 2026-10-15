package com.subdual.research_service.research.factory;

import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.dataset.DatasetPersistenceClient;
import com.subdual.research_service.integration.dataset.NoOpDatasetPersistenceClient;
import com.subdual.research_service.integration.web.DefaultWebContentFetcher;
import com.subdual.research_service.integration.web.WebContentFetcher;
import com.subdual.research_service.research.discovery.provider.MockSearchProvider;
import com.subdual.research_service.research.discovery.provider.SearchProvider;

/**
 * Concrete factory manufacturing offline/deterministic components for testing and isolated execution.
 */
public class OfflineResearchComponentFactory implements ResearchComponentFactory {

    @Override
    public SearchProvider createSearchProvider() {
        return new MockSearchProvider();
    }

    @Override
    public AiExtractionClient createAiExtractionClient() {
        return new NoOpAiExtractionClient();
    }

    @Override
    public DatasetPersistenceClient createDatasetPersistenceClient() {
        return new NoOpDatasetPersistenceClient();
    }

    @Override
    public WebContentFetcher createWebContentFetcher() {
        return new DefaultWebContentFetcher(true);
    }
}
