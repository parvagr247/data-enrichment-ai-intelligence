package com.subdual.research_service.discovery;

import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.research.model.DiscoveredSource;
import java.util.List;

public class TavilyResearchSourceClient extends TavilySearchProvider implements ResearchSourceClient {

    public TavilyResearchSourceClient(ResearchDiscoveryProperties properties) {
        super(properties);
    }

    @Override
    public List<DiscoveredSource> discoverSources(String query, int maxResults) {
        return search(query, maxResults);
    }
}
