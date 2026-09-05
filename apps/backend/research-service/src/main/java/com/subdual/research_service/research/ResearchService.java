package com.subdual.research_service.research;

import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;

public interface ResearchService {

    ResearchResponse executeResearch(ResearchRequest request);

    default ResearchResponse research(ResearchRequest request) {
        return executeResearch(request);
    }
}
