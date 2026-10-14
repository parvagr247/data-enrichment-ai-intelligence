package com.subdual.research_service.discovery.factory;

import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.discovery.provider.SearchProvider;

/**
 * Factory interface for creating SearchProvider instances based on provider type.
 */
public interface SearchProviderFactory {
    SearchProvider createSearchProvider(String providerName, ResearchDiscoveryProperties properties);
}
