package com.subdual.research_service.config;

import com.subdual.research_service.discovery.MockSearchProvider;
import com.subdual.research_service.discovery.ResearchSourceClient;
import com.subdual.research_service.discovery.SearchProvider;
import com.subdual.research_service.discovery.TavilySearchProvider;
import com.subdual.research_service.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Configuration
@Slf4j
public class ResearchConfiguration {

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

    @Bean
    public ResearchSourceClient researchSourceClient(SearchProvider searchProvider) {
        if (searchProvider instanceof ResearchSourceClient client) {
            return client;
        }
        return (query, maxResults) -> searchProvider.discoverSources(query, maxResults);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
