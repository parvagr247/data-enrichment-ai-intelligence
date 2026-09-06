package com.subdual.ai_intelligent_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.extraction")
public record AiProperties(
        String model,
        double temperature,
        boolean mockMode
) {
    public AiProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-3.5-flash-lite";
        }
        if (temperature < 0.0 || temperature > 2.0) {
            temperature = 0.1;
        }
    }
}
