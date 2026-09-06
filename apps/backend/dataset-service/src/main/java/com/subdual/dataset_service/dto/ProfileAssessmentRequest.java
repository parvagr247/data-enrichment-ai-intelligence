package com.subdual.dataset_service.dto;

import com.subdual.dataset_service.profile.model.ResearchObjective;

import java.util.List;
import java.util.Map;

public record ProfileAssessmentRequest(
        Map<String, String> rawInput,
        String displayName,
        String canonicalUrl,
        String entityType,
        ResearchObjective objective,
        Map<String, FactEvidenceDto> researchEvidence,
        List<String> sourceUrls,
        List<String> sourceSnippets
) {
    public ProfileAssessmentRequest {
        if (rawInput == null) rawInput = Map.of();
        if (objective == null) objective = ResearchObjective.blank();
        if (researchEvidence == null) researchEvidence = Map.of();
        if (sourceUrls == null) sourceUrls = List.of();
        if (sourceSnippets == null) sourceSnippets = List.of();
    }
}
