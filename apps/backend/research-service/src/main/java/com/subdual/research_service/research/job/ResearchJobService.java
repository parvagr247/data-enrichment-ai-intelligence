package com.subdual.research_service.research.job;

import com.subdual.research_service.api.dto.ResearchJobResponse;
import com.subdual.research_service.api.dto.ResearchRequest;

import java.util.Optional;

public interface ResearchJobService {
    ResearchJobResponse submitJob(ResearchRequest request);
    Optional<ResearchJobResponse> getJob(String jobId);
}
