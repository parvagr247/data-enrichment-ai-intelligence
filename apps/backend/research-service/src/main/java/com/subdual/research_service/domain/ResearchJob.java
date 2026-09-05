package com.subdual.research_service.domain;

import com.subdual.research_service.dto.request.ResearchRequest;
import com.subdual.research_service.dto.response.ResearchResponse;

import java.time.Instant;

public record ResearchJob(
        String jobId,
        ResearchRequest request,
        ResearchJobStatus status,
        int progress,
        Instant createdAt,
        Instant completedAt,
        ResearchResponse result,
        String error
) {
    public static ResearchJob submitted(String jobId, ResearchRequest request) {
        return new ResearchJob(jobId, request, ResearchJobStatus.SUBMITTED, 0, Instant.now(), null, null, null);
    }

    public ResearchJob withStatus(ResearchJobStatus newStatus, int newProgress) {
        return new ResearchJob(jobId, request, newStatus, newProgress, createdAt, completedAt, result, error);
    }

    public ResearchJob withCompleted(ResearchResponse response) {
        return new ResearchJob(jobId, request, ResearchJobStatus.COMPLETED, 100, createdAt, Instant.now(), response, null);
    }

    public ResearchJob withFailed(String errorMessage) {
        return new ResearchJob(jobId, request, ResearchJobStatus.FAILED, 100, createdAt, Instant.now(), null, errorMessage);
    }
}
