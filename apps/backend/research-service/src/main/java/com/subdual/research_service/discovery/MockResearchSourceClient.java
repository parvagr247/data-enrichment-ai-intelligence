package com.subdual.research_service.discovery;

import com.subdual.research_service.research.model.DiscoveredSource;
import java.util.List;

public class MockResearchSourceClient extends MockSearchProvider implements ResearchSourceClient {

    @Override
    public List<DiscoveredSource> discoverSources(String query, int maxResults) {
        return search(query, maxResults);
    }
}
