package com.subdual.dataset_service.profile.model;

import java.util.List;
import java.util.Map;

public record ObjectiveAssessment(
        int overallScore,
        PriorityTier priorityTier,
        String whyRelevant,
        Map<String, DimensionalScore> dimensions,
        List<String> keyStrengths,
        List<String> limitationsOrGaps
) {
    public ObjectiveAssessment {
        if (priorityTier == null) priorityTier = PriorityTier.NONE;
        if (whyRelevant == null || whyRelevant.isBlank()) whyRelevant = "No specific relevance assessment generated.";
        if (dimensions == null) dimensions = Map.of();
        if (keyStrengths == null) keyStrengths = List.of();
        if (limitationsOrGaps == null) limitationsOrGaps = List.of();
    }

    public static ObjectiveAssessment neutral(String message) {
        return new ObjectiveAssessment(
                0,
                PriorityTier.NONE,
                message != null ? message : "General profile research completed (no specific objective specified).",
                Map.of(),
                List.of(),
                List.of()
        );
    }

    public enum PriorityTier {
        HIGH,
        MEDIUM,
        LOW,
        NONE
    }

    public record DimensionalScore(
            int score,
            String rationale
    ) {}
}
