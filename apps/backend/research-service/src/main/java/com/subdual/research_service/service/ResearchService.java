package com.subdual.research_service.service;

import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;

public interface ResearchService {
    ResearchResponse executeResearch(ResearchRequest request);

    default ResearchResponse research(ResearchRequest request) {
        return executeResearch(request);
    }
}
