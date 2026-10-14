package com.subdual.ai_intelligent_service.capability.profile.dto;

import com.subdual.ai_intelligent_service.ai.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.capability.profile.model.ObjectiveAssessment;
import com.subdual.ai_intelligent_service.capability.profile.model.RecommendedApproach;
import com.subdual.ai_intelligent_service.capability.profile.model.ResearchFinding;
import com.subdual.ai_intelligent_service.capability.profile.model.ResearchProfile;

import java.util.List;

public record ProfileAssessmentResponse(
        String displayName,
        String canonicalUrl,
        String entityType,
        ResearchProfile profile,
        ObjectiveAssessment assessment,
        RecommendedApproach recommendation,
        List<ResearchFinding> findings,
        AiExecutionMetrics executionMetrics
) {
    public ProfileAssessmentResponse {
        if (findings == null) findings = List.of();
    }
}
