package com.subdual.research_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "research.web-fetch")
public record WebFetchProperties(
        int connectTimeoutMs,
        int readTimeoutMs,
        int maxResponseSizeMb,
        String userAgent
) {
    public WebFetchProperties {
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 3000;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 5000;
        }
        if (maxResponseSizeMb <= 0) {
            maxResponseSizeMb = 5;
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "DataEnrichmentBot/1.0 (+https://github.com/subdual/data-enrichment)";
        }
    }
}
