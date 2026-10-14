package com.subdual.research_service.facade;

import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.api.dto.response.ResearchJobResponse;
import com.subdual.research_service.api.dto.response.ResearchResponse;
import com.subdual.research_service.research.job.ResearchJobService;
import com.subdual.research_service.research.service.ResearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Concrete implementation of ResearchPipelineFacade coordinating the core research orchestrator
 * and background job tracking services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultResearchPipelineFacade implements ResearchPipelineFacade {

    private final ResearchService researchService;
    private final ResearchJobService researchJobService;

    @Override
    public ResearchResponse executeResearch(ResearchRequest request) {
        log.info("ResearchPipelineFacade: executing synchronous research for entity='{}', url='{}'",
                request != null ? request.name() : null, request != null ? request.url() : null);
        return researchService.executeResearch(request);
    }

    @Override
    public ResearchJobResponse submitJob(ResearchRequest request, String userId) {
        log.info("ResearchPipelineFacade: submitting asynchronous research job for user='{}'", userId);
        return (userId != null && !userId.isBlank())
                ? researchJobService.submitJob(request, userId)
                : researchJobService.submitJob(request);
    }

    @Override
    public Optional<ResearchJobResponse> getJob(String jobId, String userId) {
        return (userId != null && !userId.isBlank())
                ? researchJobService.getJob(jobId, userId)
                : researchJobService.getJob(jobId);
    }
}
