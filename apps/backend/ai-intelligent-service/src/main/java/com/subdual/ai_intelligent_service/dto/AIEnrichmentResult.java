package com.subdual.ai_intelligent_service.dto;

import java.util.List;
import java.util.Map;

public record AIEnrichmentResult(
        String displayName,
        String entityType,
        String canonicalUrl,
        Map<String, EnrichedAttributeResult> attributes,
        List<String> unresolvedFields,
        List<String> conflicts,
        double overallConfidence,
        String modelUsed,
        long executionTimeMs
) {
    public AIEnrichmentResult {
        if (attributes == null) {
            attributes = Map.of();
        }
        if (unresolvedFields == null) {
            unresolvedFields = List.of();
        }
        if (conflicts == null) {
            conflicts = List.of();
        }
    }
}
