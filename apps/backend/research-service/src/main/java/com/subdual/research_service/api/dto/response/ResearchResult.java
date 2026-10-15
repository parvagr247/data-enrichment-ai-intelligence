package com.subdual.research_service.api.dto.response;

import java.util.Map;

import com.subdual.research_service.research.model.EntityType;

public record ResearchResult(
        String displayName,
        EntityType entityType,
        String canonicalUrl,
        Map<String, EvidenceTuple> attributes
) {}
