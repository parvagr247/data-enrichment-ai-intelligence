package com.subdual.research_service.client;

import com.subdual.research_service.domain.DiscoveredSource;
import java.util.List;

/**
 * Primary abstraction for external web search and source discovery.
 * Decouples the research engine from specific search provider implementations.
 */
public interface SearchDiscoveryProvider {

    /**
     * Discovers candidate sources matching the specified search query.
     *
     * @param query      the structured search query
     * @param maxResults the maximum number of results to retrieve
     * @return list of discovered sources
     */
    List<DiscoveredSource> discover(String query, int maxResults);

    /**
     * Backward-compatible alias for discover.
     */
    default List<DiscoveredSource> search(String query, int maxResults) {
        return discover(query, maxResults);
    }

    /**
     * Backward-compatible alias for discover.
     */
    default List<DiscoveredSource> discoverSources(String query, int maxResults) {
        return discover(query, maxResults);
    }
}
