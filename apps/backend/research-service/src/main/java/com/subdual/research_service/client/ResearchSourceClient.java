package com.subdual.research_service.client;

import com.subdual.research_service.discovery.SearchProvider;
import com.subdual.research_service.domain.DiscoveredSource;
import java.util.List;

@Deprecated
@FunctionalInterface
public interface ResearchSourceClient extends SearchProvider {

    @Override
    default List<DiscoveredSource> search(String query, int maxResults) {
        return discoverSources(query, maxResults);
    }

    @Override
    List<DiscoveredSource> discoverSources(String query, int maxResults);
}
