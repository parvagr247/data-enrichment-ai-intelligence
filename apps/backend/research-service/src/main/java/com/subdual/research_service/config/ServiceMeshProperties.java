package com.subdual.research_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "research.services")
public record ServiceMeshProperties(
        String aiIntelligentServiceUrl,
        String datasetServiceUrl
) {
    public ServiceMeshProperties {
        if (aiIntelligentServiceUrl == null || aiIntelligentServiceUrl.isBlank()) {
            aiIntelligentServiceUrl = "http://localhost:9742";
        }
        if (datasetServiceUrl == null || datasetServiceUrl.isBlank()) {
            datasetServiceUrl = "http://localhost:9743";
        }
    }
}
