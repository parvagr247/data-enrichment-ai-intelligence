package com.subdual.research_service.research.job;

import java.util.Optional;

import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.response.ResearchJobResponse;

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
