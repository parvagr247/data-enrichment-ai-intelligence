package com.subdual.research_service.client;

import com.subdual.research_service.domain.DiscoveredSource;
import java.util.List;

public interface SearchProvider extends SearchDiscoveryProvider {

    @Override
    default List<DiscoveredSource> discover(String query, int maxResults) {
        return search(query, maxResults);
    }

    List<DiscoveredSource> search(String query, int maxResults);
}
