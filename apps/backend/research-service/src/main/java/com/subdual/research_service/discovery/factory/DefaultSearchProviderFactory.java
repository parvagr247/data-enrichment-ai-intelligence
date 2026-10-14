package com.subdual.research_service.discovery.factory;

import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.discovery.provider.MockSearchProvider;
import com.subdual.research_service.discovery.provider.ResilientSearchProviderDecorator;
import com.subdual.research_service.discovery.provider.SearchProvider;
import com.subdual.research_service.discovery.provider.TavilySearchProvider;
import org.springframework.stereotype.Component;

import java.util.Locale;

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
