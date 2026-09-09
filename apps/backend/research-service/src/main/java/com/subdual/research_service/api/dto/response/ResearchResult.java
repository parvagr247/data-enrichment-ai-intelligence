package com.subdual.research_service.api.dto.response;

import com.subdual.research_service.research.model.EntityType;
import java.util.Map;

public record ResearchResult(
        String displayName,
        EntityType entityType,
        String canonicalUrl,
        Map<String, EvidenceTuple> attributes
) {}
