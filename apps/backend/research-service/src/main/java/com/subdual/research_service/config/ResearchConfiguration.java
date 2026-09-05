package com.subdual.research_service.config;

import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.discovery.provider.MockSearchProvider;
import com.subdual.research_service.discovery.provider.SearchProvider;
import com.subdual.research_service.discovery.provider.TavilySearchProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@Slf4j
public class ResearchConfiguration {

    @Bean
    @Primary
    public SearchProvider searchProvider(ResearchDiscoveryProperties properties) {
        String providerName = resolveProviderName(properties);
        log.info("Initializing active SearchProvider: '{}'", providerName);
        return createSearchProvider(providerName, properties);
    }

    private String resolveProviderName(ResearchDiscoveryProperties properties) {
        return (properties != null && properties.provider() != null)
                ? properties.provider().trim().toLowerCase(Locale.ROOT)
                : "mock";
    }

    private SearchProvider createSearchProvider(String providerName, ResearchDiscoveryProperties properties) {
        if ("tavily".equals(providerName)) {
            return new TavilySearchProvider(properties);
        }
        if ("mock".equals(providerName)) {
            return new MockSearchProvider();
        }
        throw new BusinessRuleException("Unsupported search provider configured: '" + properties.provider()
                + "'. Supported providers are 'mock' and 'tavily'.");
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
