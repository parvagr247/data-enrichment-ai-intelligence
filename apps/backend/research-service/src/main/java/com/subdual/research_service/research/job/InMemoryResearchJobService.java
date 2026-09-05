package com.subdual.research_service.research.job;

import com.subdual.research_service.api.dto.ResearchJobResponse;
import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;
import com.subdual.research_service.research.ResearchService;
import com.subdual.research_service.research.model.ResearchJob;
import com.subdual.research_service.research.model.ResearchJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
@Slf4j
public class InMemoryResearchJobService implements ResearchJobService, DisposableBean {

    private final ResearchService researchService;
    private final ExecutorService executor;
    private final Map<String, ResearchJob> jobs = new ConcurrentHashMap<>();

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
                    jobId, response != null ? response.executionTimeMs() : 0);

        } catch (Exception ex) {
            log.error("[AsyncJob: FAILED] Research job failed for jobId='{}': {}", jobId, ex.getMessage(), ex);
            updateJob(jobId, job -> job.withFailed(ex.getMessage()));
        } finally {
            MDC.remove("jobId");
        }
    }

    private void updateJob(String jobId, UnaryOperator<ResearchJob> updater) {
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
