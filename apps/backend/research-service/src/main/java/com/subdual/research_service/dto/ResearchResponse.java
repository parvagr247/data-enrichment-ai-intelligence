package com.subdual.research_service.dto;

import com.subdual.research_service.domain.ResearchStatus;
import java.util.List;

public record ResearchResponse(
        ResearchStatus status,
        String entityId,
        ResearchResult result,
        List<SourceItem> sources,
        long executionTimeMs
) {}
