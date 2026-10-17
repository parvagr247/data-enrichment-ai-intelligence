package com.subdual.dataset_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Validated 
@ConfigurationProperties(prefix = "enrichment")
public record EnrichmentProperties(
        @Valid Execution execution,
        @Valid Research research,
        @Valid Timeout timeout,
        @Valid Ai ai
) {

    public record Execution( @Min(1) int concurrency, @Min(10) int queueCapacity ) {}

    public record Research( @Min(1) int maxSourcesPerEntity ) {}

    public record Timeout( @Min(5) long entitySeconds, @Min(1) long jobMinutes ) {}

    public record Ai(boolean enabled) {}
}
