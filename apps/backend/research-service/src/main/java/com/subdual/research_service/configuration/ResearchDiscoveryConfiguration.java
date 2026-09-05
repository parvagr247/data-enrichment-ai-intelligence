package com.subdual.research_service.configuration;

import com.subdual.research_service.client.ResearchSourceClient;
import com.subdual.research_service.discovery.MockSearchProvider;
import com.subdual.research_service.discovery.SearchProvider;
import com.subdual.research_service.discovery.TavilySearchProvider;
import com.subdual.research_service.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Locale;

@Configuration
public class ResearchDiscoveryConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ResearchDiscoveryConfiguration.class);

    @Bean
    @Primary
    public SearchProvider searchProvider(ResearchDiscoveryProperties properties) {
        String providerName = properties.provider() != null ? properties.provider().trim().toLowerCase(Locale.ROOT) : "mock";
        log.info("Initializing active SearchProvider: '{}'", providerName);

        if ("tavily".equals(providerName)) {
            return new TavilySearchProvider(properties);
        } else if ("mock".equals(providerName)) {
            return new MockSearchProvider();
        } else {
            throw new BusinessRuleException("Unsupported search provider configured: '" + properties.provider()
                    + "'. Supported providers are 'mock' and 'tavily'.");
        }
    }

    /**
     * Backward-compatible bean for legacy tests expecting ResearchSourceClient.
     */
    @Bean
    public ResearchSourceClient researchSourceClient(SearchProvider searchProvider) {
        if (searchProvider instanceof ResearchSourceClient client) {
            return client;
        }
        return (query, maxResults) -> searchProvider.search(query, maxResults);
    }
}
