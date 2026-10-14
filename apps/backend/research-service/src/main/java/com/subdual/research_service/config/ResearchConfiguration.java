package com.subdual.research_service.config;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import com.subdual.research_service.discovery.factory.SearchProviderFactory;
import com.subdual.research_service.discovery.provider.SearchProvider;

@Configuration
@Slf4j
public class ResearchConfiguration {

    @Bean
    @Primary
    public SearchProvider searchProvider(ResearchDiscoveryProperties properties, SearchProviderFactory searchProviderFactory) {
        String providerName = resolveProviderName(properties);
        return searchProviderFactory.createSearchProvider(providerName, properties);
    }

    private String resolveProviderName(ResearchDiscoveryProperties properties) {
        return (properties != null && properties.provider() != null)
                ? properties.provider().trim().toLowerCase(Locale.ROOT)
                : "mock";
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService researchJobExecutor() {
        return createResearchThreadPool();
    }

    private ExecutorService createResearchThreadPool() {
        AtomicInteger workerNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "research-worker-" + workerNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        return new ThreadPoolExecutor(
                4,
                16,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
