package com.subdual.dataset_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Validated 
@ConfigurationProperties(prefix = "enrichment")
public record EnrichmentProperties(
        @Valid Execution execution
) {
    public EnrichmentProperties {
        if (execution == null) {
            execution = new Execution(3, 500);
        }
    }

    public record Execution( @Min(1) int concurrency, @Min(10) int queueCapacity ) {
        public Execution {
            if (concurrency < 1) concurrency = 1;
            if (queueCapacity < 10) queueCapacity = 100;
        }
    }
}
