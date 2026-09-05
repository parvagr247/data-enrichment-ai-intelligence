package com.subdual.research_service.service;

import com.subdual.research_service.dto.request.ResearchRequest;
import com.subdual.research_service.dto.response.ResearchResponse;

public interface ResearchService {
    ResearchResponse executeResearch(ResearchRequest request);

    default ResearchResponse research(ResearchRequest request) {
        return executeResearch(request);
    }
}
