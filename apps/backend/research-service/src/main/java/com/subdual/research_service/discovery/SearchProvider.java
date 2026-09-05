package com.subdual.research_service.discovery;

import com.subdual.research_service.domain.DiscoveredSource;
import java.util.List;

/**
 * Strategy interface for external web search and source discovery.
 * Decouples research orchestration from specific search engine providers.
 */
public interface SearchProvider {

    /**
     * Executes a search query and returns candidate discovered sources.
     *
     * @param query      the structured discovery query
     * @param maxResults maximum candidate sources to retrieve
     * @return list of discovered sources
     */
    List<DiscoveredSource> search(String query, int maxResults);

    /**
     * Backward-compatible alias for search.
     */
    default List<DiscoveredSource> discoverSources(String query, int maxResults) {
        return search(query, maxResults);
    }
}
