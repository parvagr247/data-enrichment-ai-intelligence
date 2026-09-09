package com.subdual.ai_intelligent_service.enrichment.api.dto;

import java.util.List;
import java.util.Map;

public record EnrichmentSynthesisRequest(
        Map<String, String> rawInput,
        String displayName,
        String entityType,
        String canonicalUrl,
        String userRequirement,
        List<String> targetFields,
        Map<String, FactEvidenceDto> researchEvidence,
        List<String> researchSources
) {
    public EnrichmentSynthesisRequest {
        if (rawInput == null) {
            rawInput = Map.of();
        }
        if (targetFields == null) {
            targetFields = List.of();
        }
        if (researchEvidence == null) {
            researchEvidence = Map.of();
        }
        if (researchSources == null) {
            researchSources = List.of();
        }
    }
}
