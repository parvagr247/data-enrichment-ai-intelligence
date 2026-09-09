package com.subdual.research_service.research.job;

import com.subdual.research_service.research.api.ResearchRequest;
import com.subdual.research_service.research.api.ResearchResponse;

import java.time.Instant;

public record ResearchJob(
        String jobId,
        String userId,
        ResearchRequest request,
        ResearchJobStatus status,
        int progress,
        Instant createdAt,
        Instant completedAt,
        ResearchResponse result,
        String error
) {
    public static ResearchJob submitted(String jobId, String userId, ResearchRequest request) {
        return new ResearchJob(jobId, userId, request, ResearchJobStatus.SUBMITTED, 0, Instant.now(), null, null, null);
    }

    public static ResearchJob submitted(String jobId, ResearchRequest request) {
        return submitted(jobId, null, request);
    }

    public ResearchJob withStatus(ResearchJobStatus newStatus, int newProgress) {
        return new ResearchJob(jobId, userId, request, newStatus, newProgress, createdAt, completedAt, result, error);
    }

    public ResearchJob withCompleted(ResearchResponse response) {
        return new ResearchJob(jobId, userId, request, ResearchJobStatus.COMPLETED, 100, createdAt, Instant.now(), response, null);
    }

    public ResearchJob withFailed(String errorMessage) {
        return new ResearchJob(jobId, userId, request, ResearchJobStatus.FAILED, 100, createdAt, Instant.now(), null, errorMessage);
    }
}
