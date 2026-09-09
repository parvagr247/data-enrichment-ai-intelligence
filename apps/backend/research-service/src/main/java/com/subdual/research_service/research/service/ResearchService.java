package com.subdual.research_service.research.service;

import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.api.dto.response.ResearchResponse;

public interface ResearchService {

    ResearchResponse executeResearch(ResearchRequest request);

    default ResearchResponse research(ResearchRequest request) {
        return executeResearch(request);
    }
}
