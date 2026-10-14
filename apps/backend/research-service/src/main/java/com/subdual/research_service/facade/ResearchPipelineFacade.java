package com.subdual.research_service.facade;

import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.api.dto.response.ResearchJobResponse;
import com.subdual.research_service.api.dto.response.ResearchResponse;

import java.util.Optional;

/**
 * GoF Facade Pattern: Unified facade for the Research Service subsystems,
 * encapsulating synchronous pipeline orchestration, background asynchronous jobs,
 * source discovery, and multi-source evidence compilation.
 */
public interface ResearchPipelineFacade {

    ResearchResponse executeResearch(ResearchRequest request);

    ResearchJobResponse submitJob(ResearchRequest request, String userId);

    Optional<ResearchJobResponse> getJob(String jobId, String userId);
}
