package com.subdual.dataset_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class DatasetServiceConfiguration {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService enrichmentJobExecutor() {
        AtomicInteger workerNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "dataset-enrichment-worker-" + workerNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        return new ThreadPoolExecutor(
                2,
                8,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
