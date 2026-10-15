package com.subdual.research_service.research.discovery.factory;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.research.discovery.provider.MockSearchProvider;
import com.subdual.research_service.research.discovery.provider.ResilientSearchProviderDecorator;
import com.subdual.research_service.research.discovery.provider.SearchProvider;
import com.subdual.research_service.research.discovery.provider.TavilySearchProvider;
import com.subdual.research_service.shared.exception.BusinessRuleException;

/**
 * Default implementation of SearchProviderFactory creating Tavily (resilient) or Mock search providers.
 */
@Component
public class DefaultSearchProviderFactory implements SearchProviderFactory {

    @Override
    public SearchProvider createSearchProvider(String providerName, ResearchDiscoveryProperties properties) {
        String normalized = (providerName != null) ? providerName.trim().toLowerCase(Locale.ROOT) : "mock";
        if ("tavily".equals(normalized)) {
            SearchProvider primary = new TavilySearchProvider(properties);
            SearchProvider fallback = new MockSearchProvider();
            return new ResilientSearchProviderDecorator(primary, fallback);
        }
        if ("mock".equals(normalized)) {
            return new MockSearchProvider();
        }
        throw new BusinessRuleException("Unsupported search provider configured: '" + providerName
                + "'. Supported providers are 'mock' and 'tavily'.");
    }
}
