package com.subdual.dataset_service.dto.response;

import com.subdual.dataset_service.profile.model.ObjectiveAssessment;
import com.subdual.dataset_service.profile.model.RecommendedApproach;
import com.subdual.dataset_service.profile.model.ResearchFinding;
import com.subdual.dataset_service.profile.model.ResearchProfile;

import java.util.List;
import java.util.Map;

public record ProfileAssessmentResponse(
        String displayName,
        String canonicalUrl,
        String entityType,
        ResearchProfile profile,
        ObjectiveAssessment assessment,
        RecommendedApproach recommendation,
        List<ResearchFinding> findings,
        Map<String, Object> executionMetrics
) {
    public ProfileAssessmentResponse {
        if (findings == null) findings = List.of();
        if (executionMetrics == null) executionMetrics = Map.of();
    }
}
