package com.subdual.research_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "research.discovery")
public record ResearchDiscoveryProperties(
        String provider,
        String apiKey,
        String baseUrl,
        int maxResults,
        int timeoutMs
) {
    public ResearchDiscoveryProperties {
        if (provider == null || provider.isBlank()) {
            provider = "mock";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.tavily.com";
        }
        if (maxResults <= 0) {
            maxResults = 5;
        }
        if (timeoutMs <= 0) {
            timeoutMs = 4000;
        }
    }
}
