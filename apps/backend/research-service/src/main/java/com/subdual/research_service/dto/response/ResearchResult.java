package com.subdual.research_service.dto.response;

import com.subdual.research_service.domain.EntityType;
import java.util.Map;

public record ResearchResult(
        String displayName,
        EntityType entityType,
        String canonicalUrl,
        Map<String, EvidenceTuple> attributes
) {}
