package com.subdual.research_service.client;

import com.subdual.research_service.discovery.MockSearchProvider;

/**
 * Backward-compatible adapter for legacy tests.
 */
public class MockResearchSourceClient extends MockSearchProvider implements ResearchSourceClient {

    @Override
    public java.util.List<com.subdual.research_service.domain.DiscoveredSource> discoverSources(String query, int maxResults) {
        return search(query, maxResults);
    }
}
