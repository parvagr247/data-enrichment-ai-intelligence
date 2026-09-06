package com.subdual.dataset_service.service.executor;

import com.subdual.dataset_service.config.EnrichmentProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Spring-managed bounded executor for dataset entity enrichment tasks.
 * Enforces strict bounded concurrency, bounded queue capacity, backpressure,
 * thread-safe progress isolation, and graceful shutdown.
 */
@Component
@Slf4j
public class EnrichmentTaskExecutor {

    private final ThreadPoolExecutor executor;
    private final int concurrency;

    public EnrichmentTaskExecutor(EnrichmentProperties properties) {
        this.concurrency = properties.execution().concurrency();
        int queueCapacity = properties.execution().queueCapacity();

        AtomicInteger workerNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "dataset-enrichment-worker-" + workerNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        this.executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        this.executor.allowCoreThreadTimeOut(false);

        log.info("Initialized EnrichmentTaskExecutor with concurrency={}, queueCapacity={}",
                concurrency, queueCapacity);
    }

    /**
     * Submits an asynchronous enrichment task isolated to this bounded executor.
     */
    public <T> CompletableFuture<T> submitTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    public int getConcurrency() {
        return concurrency;
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down EnrichmentTaskExecutor gracefully...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("EnrichmentTaskExecutor did not terminate within 5s, forcing shutdownNow");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted during EnrichmentTaskExecutor shutdown: {}", e.getMessage());
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
