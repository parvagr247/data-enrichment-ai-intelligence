package com.subdual.dataset_service.profile.model;

public record ResearchFinding(
        String claim,
        FindingType findingType,
        String confidence,
        String sourceUrl,
        String evidenceSnippet,
        String sourceTitle
) {
    public ResearchFinding {
        if (findingType == null) findingType = FindingType.FACT_SOURCE_DERIVED;
        if (confidence == null || confidence.isBlank()) confidence = "MEDIUM";
        if (evidenceSnippet == null) evidenceSnippet = "";
    }

    public enum FindingType {
        FACT_SOURCE_DERIVED,
        INFERRED_ASSESSMENT
    }
}
