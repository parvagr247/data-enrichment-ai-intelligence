package com.subdual.research_service.api.dto;

import com.subdual.research_service.research.model.ResearchJobStatus;

import java.time.Instant;
import java.util.List;

public record ResearchJobResponse(
        String jobId,
        ResearchJobStatus status,
        int progress,
        Instant createdAt,
        Instant completedAt,
        Long durationMs,
        ResearchResponse result,
        String error,
        List<String> warnings
) {

        // created constructor with these 7 parameters only
    public ResearchJobResponse(
            String jobId,
            ResearchJobStatus status,
            int progress,
            Instant createdAt,
            Instant completedAt,
            ResearchResponse result,
            String error
    ) {
        this(
                jobId,
                status,
                progress,
                createdAt,
                completedAt,
                completedAt != null && createdAt != null ? java.time.Duration.between(createdAt, completedAt).toMillis() : null,
                result,
                error,
                result != null && result.warnings() != null ? result.warnings() : List.of()
        );
    }
}
