package com.subdual.research_service.orchestration;

import com.subdual.research_service.dto.response.ResearchResponse;

/**
 * Pipeline abstraction coordinating sequential research stages.
 */
public interface ResearchPipeline {

    ResearchResponse execute(ResearchContext context);
}
