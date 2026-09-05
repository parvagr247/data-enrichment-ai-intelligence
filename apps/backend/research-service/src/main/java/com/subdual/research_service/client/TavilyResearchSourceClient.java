package com.subdual.research_service.client;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.discovery.TavilySearchProvider;

/**
 * Backward-compatible adapter for legacy tests.
 */
public class TavilyResearchSourceClient extends TavilySearchProvider implements ResearchSourceClient {
    public TavilyResearchSourceClient(ResearchDiscoveryProperties properties) {
        super(properties);
    }

    @Override
    public java.util.List<com.subdual.research_service.domain.DiscoveredSource> discoverSources(String query, int maxResults) {
        return search(query, maxResults);
    }
}
