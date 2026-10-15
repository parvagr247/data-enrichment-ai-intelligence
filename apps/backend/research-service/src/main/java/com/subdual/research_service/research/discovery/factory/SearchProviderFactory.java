package com.subdual.research_service.research.discovery.factory;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.research.discovery.provider.SearchProvider;

/**
 * Factory interface for creating SearchProvider instances based on provider type.
 */
public interface SearchProviderFactory {
    SearchProvider createSearchProvider(String providerName, ResearchDiscoveryProperties properties);
}
