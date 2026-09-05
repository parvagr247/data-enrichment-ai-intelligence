package com.subdual.ai_intelligent_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiIntelligentServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiIntelligentServiceApplication.class, args);
	}

}

