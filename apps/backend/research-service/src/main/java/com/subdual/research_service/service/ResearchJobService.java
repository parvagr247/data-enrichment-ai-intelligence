package com.subdual.research_service.service;

import com.subdual.research_service.dto.response.ResearchJobResponse;
import com.subdual.research_service.dto.request.ResearchRequest;

import java.util.Optional;

public interface ResearchJobService {
    ResearchJobResponse submitJob(ResearchRequest request);
    Optional<ResearchJobResponse> getJob(String jobId);
}
