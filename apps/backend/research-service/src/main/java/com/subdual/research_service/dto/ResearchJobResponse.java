package com.subdual.research_service.dto;

import com.subdual.research_service.domain.ResearchJobStatus;

import java.time.Instant;

public record ResearchJobResponse(
        String jobId,
        ResearchJobStatus status,
        int progress,
        Instant createdAt,
        Instant completedAt,
        ResearchResponse result,
        String error
) {}
