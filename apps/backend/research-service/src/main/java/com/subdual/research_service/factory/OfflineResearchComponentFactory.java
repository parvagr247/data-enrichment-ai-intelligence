package com.subdual.research_service.factory;

import com.subdual.research_service.discovery.provider.MockSearchProvider;
import com.subdual.research_service.discovery.provider.SearchProvider;
import com.subdual.research_service.integration.ai.client.AiExtractionClient;
import com.subdual.research_service.integration.ai.client.NoOpAiExtractionClient;
import com.subdual.research_service.integration.persistence.client.DatasetPersistenceClient;
import com.subdual.research_service.integration.persistence.client.NoOpDatasetPersistenceClient;
import com.subdual.research_service.integration.web.DefaultWebContentFetcher;
import com.subdual.research_service.integration.web.WebContentFetcher;

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
