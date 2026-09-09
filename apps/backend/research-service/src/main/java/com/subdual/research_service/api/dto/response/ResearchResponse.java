package com.subdual.research_service.api.dto.response;

import com.subdual.research_service.research.model.ResearchStatus;
import java.util.List;
import java.util.Map;

public record ResearchResponse(
        ResearchStatus status,
        String entityId,
        ResearchResult result,
        List<SourceItem> sources,
        long executionTimeMs,
        Map<String, Object> metadata,
        List<String> warnings
) {
    public ResearchResponse(ResearchStatus status, String entityId, ResearchResult result, List<SourceItem> sources, long executionTimeMs) {
        this(status, entityId, result, sources, executionTimeMs, Map.of(), List.of());
    }

    public ResearchResponse(ResearchStatus status, String entityId, ResearchResult result, List<SourceItem> sources, long executionTimeMs, Map<String, Object> metadata) {
        this(status, entityId, result, sources, executionTimeMs, metadata, List.of());
    }
}
