package com.subdual.research_service.configuration;

import com.subdual.research_service.client.MockResearchSourceClient;
import com.subdual.research_service.client.ResearchSourceClient;
import com.subdual.research_service.client.TavilyResearchSourceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResearchDiscoveryConfiguration {

    @Bean
    @ConditionalOnProperty(name = "research.discovery.provider", havingValue = "tavily")
    public ResearchSourceClient tavilyResearchSourceClient(ResearchDiscoveryProperties properties) {
        return new TavilyResearchSourceClient(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "research.discovery.provider", havingValue = "mock", matchIfMissing = true)
    public ResearchSourceClient mockResearchSourceClient() {
        return new MockResearchSourceClient();
    }
}
