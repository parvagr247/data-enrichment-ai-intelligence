package com.subdual.ai_intelligent_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.extraction")
public record ExtractionProperties(
        String model,
        double temperature,
        boolean mockMode
) {
    public ExtractionProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-2.5-flash";
        }
        if (temperature < 0.0 || temperature > 2.0) {
            temperature = 0.1;
        }
    }
}
