package com.subdual.research_service.research.discovery.provider;

import java.util.List;

import com.subdual.research_service.research.model.DiscoveredSource;

/**
 * Top-level provider abstraction for research source discovery.
 * Decouples the research engine from specific search providers (Tavily, Mock, etc.).
 */
public interface ResearchProvider {

    List<DiscoveredSource> search(String query, int maxResults);

    String providerName();
}
