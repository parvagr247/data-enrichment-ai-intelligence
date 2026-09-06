package com.subdual.ai_intelligent_service.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralized Spring AI configuration boundary (Task 71).
 * Manages model parameters, temperature thresholds, timeouts, and execution properties.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class SpringAiConfig {

}
