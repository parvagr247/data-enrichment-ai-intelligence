package com.subdual.research_service.research;

import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;

/**
 * Primary business boundary service for synchronous research operations.
 */
public interface ResearchService {

    /**
     * Executes the end-to-end research and entity enrichment pipeline.
     *
     * @param request user research request
     * @return grounded research response with discovered sources and evidence tuples
     */
    ResearchResponse executeResearch(ResearchRequest request);

    default ResearchResponse research(ResearchRequest request) {
        return executeResearch(request);
    }
}
