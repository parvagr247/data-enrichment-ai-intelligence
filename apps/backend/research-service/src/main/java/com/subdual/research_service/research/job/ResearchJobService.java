package com.subdual.research_service.research.job;

import com.subdual.research_service.api.dto.ResearchJobResponse;
import com.subdual.research_service.api.dto.ResearchRequest;

import java.util.Optional;

public interface ResearchJobService {

    default ResearchJobResponse submitJob(ResearchRequest request) {
        return submitJob(request, null);
    }

    ResearchJobResponse submitJob(ResearchRequest request, String userId);

    default Optional<ResearchJobResponse> getJob(String jobId) {
        return getJob(jobId, null);
    }

    Optional<ResearchJobResponse> getJob(String jobId, String userId);
}
