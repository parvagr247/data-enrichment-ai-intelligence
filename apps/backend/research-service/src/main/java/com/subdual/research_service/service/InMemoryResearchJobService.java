package com.subdual.research_service.service;

import com.subdual.research_service.domain.ResearchJob;
import com.subdual.research_service.domain.ResearchJobStatus;
import com.subdual.research_service.dto.ResearchJobResponse;
import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Production-oriented in-memory asynchronous job service.
 * Manages background execution of multi-source research tasks with bounded concurrency,
 * lifecycle state tracking, duration diagnostics, and SLF4J MDC job correlation.
 */
@Service
public class InMemoryResearchJobService implements ResearchJobService, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InMemoryResearchJobService.class);

    private final ResearchService researchService;
    private final Map<String, ResearchJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    @Autowired
    public InMemoryResearchJobService(ResearchService researchService) {
        this.researchService = researchService;
        AtomicInteger workerNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "research-worker-" + workerNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        // Bounded executor to prevent uncontrolled thread explosion and OutOfMemoryError
        this.executor = new ThreadPoolExecutor(
                4,
                16,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public InMemoryResearchJobService(ResearchService researchService, ExecutorService executor) {
        this.researchService = researchService;
        this.executor = executor;
    }

    @Override
    public ResearchJobResponse submitJob(ResearchRequest request) {
        String jobId = UUID.randomUUID().toString();
        ResearchJob initialJob = ResearchJob.submitted(jobId, request);
        jobs.put(jobId, initialJob);

        log.info("[AsyncJob: SUBMITTED] JobId='{}', URL='{}'", jobId, request.url());

        executor.submit(() -> processJob(jobId, request));

        return toResponse(initialJob);
    }

    @Override
    public Optional<ResearchJobResponse> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId)).map(this::toResponse);
    }

    private void processJob(String jobId, ResearchRequest request) {
        MDC.put("jobId", jobId);
        try {
            updateJob(jobId, job -> job.withStatus(ResearchJobStatus.IN_PROGRESS, 25));
            log.info("[AsyncJob: IN_PROGRESS] Executing research for jobId='{}'", jobId);

            ResearchResponse response = researchService.executeResearch(request);

            updateJob(jobId, job -> job.withCompleted(response));
            log.info("[AsyncJob: COMPLETED] Research completed for jobId='{}' in {}ms",
                    jobId, response.executionTimeMs());

        } catch (Exception ex) {
            log.error("[AsyncJob: FAILED] Research job failed for jobId='{}': {}", jobId, ex.getMessage(), ex);
            updateJob(jobId, job -> job.withFailed(ex.getMessage()));
        } finally {
            MDC.remove("jobId");
        }
    }

    private void updateJob(String jobId, java.util.function.UnaryOperator<ResearchJob> updater) {
        jobs.computeIfPresent(jobId, (id, current) -> updater.apply(current));
    }

    private ResearchJobResponse toResponse(ResearchJob job) {
        Long duration = (job.completedAt() != null && job.createdAt() != null)
                ? Duration.between(job.createdAt(), job.completedAt()).toMillis()
                : null;

        List<String> warnings = (job.result() != null && job.result().warnings() != null)
                ? job.result().warnings()
                : List.of();

        return new ResearchJobResponse(
                job.jobId(),
                job.status(),
                job.progress(),
                job.createdAt(),
                job.completedAt(),
                duration,
                job.result(),
                job.error(),
                warnings
        );
    }

    @Override
    public void destroy() {
        log.info("Shutting down ResearchJobService executor pool");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
