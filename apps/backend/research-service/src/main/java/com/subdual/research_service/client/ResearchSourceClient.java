package com.subdual.research_service.client;

import com.subdual.research_service.domain.DiscoveredSource;
import java.util.List;

public interface ResearchSourceClient extends SearchProvider {
    @Override
    default List<DiscoveredSource> search(String query, int maxResults) {
        return discoverSources(query, maxResults);
    }

    List<DiscoveredSource> discoverSources(String query, int maxResults);
}
