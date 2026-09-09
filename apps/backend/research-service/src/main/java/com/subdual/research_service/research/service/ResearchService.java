package com.subdual.research_service.research.service;

import com.subdual.research_service.research.api.ResearchRequest;
import com.subdual.research_service.research.api.ResearchResponse;

public interface ResearchService {

    ResearchResponse executeResearch(ResearchRequest request);

    default ResearchResponse research(ResearchRequest request) {
        return executeResearch(request);
    }
}
