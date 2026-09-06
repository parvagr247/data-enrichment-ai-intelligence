package com.subdual.dataset_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enrichment")
public record EnrichmentProperties(
        Execution execution,
        Research research,
        Timeout timeout,
        Ai ai
) {
    public EnrichmentProperties {
        if (execution == null) execution = new Execution(3, 500);
        if (research == null) research = new Research(5);
        if (timeout == null) timeout = new Timeout(60, 30);
        if (ai == null) ai = new Ai(true);
    }

    public record Execution(int concurrency, int queueCapacity) {
        public Execution {
            if (concurrency < 1) concurrency = 1;
            if (queueCapacity < 10) queueCapacity = 100;
        }
    }

    public record Research(int maxSourcesPerEntity) {
        public Research {
            if (maxSourcesPerEntity < 1) maxSourcesPerEntity = 1;
        }
    }

    public record Timeout(long entitySeconds, long jobMinutes) {
        public Timeout {
            if (entitySeconds < 5) entitySeconds = 60;
            if (jobMinutes < 1) jobMinutes = 30;
        }
    }

    public record Ai(boolean enabled) {}
}
