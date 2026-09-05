package com.subdual.research_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "research.pipeline")
public record ResearchPipelineProperties(
        int maxSources,
        int maxContentLength
) {
    public ResearchPipelineProperties {
        if (maxSources <= 0) {
            maxSources = 5;
        }
        if (maxContentLength <= 0) {
            maxContentLength = 50000;
        }
    }
}
