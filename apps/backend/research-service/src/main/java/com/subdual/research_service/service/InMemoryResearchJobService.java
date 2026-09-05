package com.subdual.research_service.service;

import com.subdual.research_service.domain.ResearchJob;
import com.subdual.research_service.domain.ResearchJobStatus;
import com.subdual.research_service.dto.ResearchJobResponse;
import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class InMemoryResearchJobService implements ResearchJobService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryResearchJobService.class);

    private final ResearchService researchService;
    private final Map<String, ResearchJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    @org.springframework.beans.factory.annotation.Autowired
    public InMemoryResearchJobService(ResearchService researchService) {
        this.researchService = researchService;
        this.executor = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "research-job-worker");
            thread.setDaemon(true);
            return thread;
        });
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
        try {
            updateJob(jobId, job -> job.withStatus(ResearchJobStatus.IN_PROGRESS, 25));
            log.info("[AsyncJob: IN_PROGRESS] Executing research for jobId='{}'", jobId);

            ResearchResponse response = researchService.executeResearch(request);

            updateJob(jobId, job -> job.withCompleted(response));
            log.info("[AsyncJob: COMPLETED] Research completed for jobId='{}'", jobId);

        } catch (Exception ex) {
            log.error("[AsyncJob: FAILED] Research job failed for jobId='{}': {}", jobId, ex.getMessage(), ex);
            updateJob(jobId, job -> job.withFailed(ex.getMessage()));
        }
    }

    private void updateJob(String jobId, java.util.function.UnaryOperator<ResearchJob> updater) {
        jobs.computeIfPresent(jobId, (id, current) -> updater.apply(current));
    }

    private ResearchJobResponse toResponse(ResearchJob job) {
        return new ResearchJobResponse(
                job.jobId(),
                job.status(),
                job.progress(),
                job.createdAt(),
                job.completedAt(),
                job.result(),
                job.error()
        );
    }
}
