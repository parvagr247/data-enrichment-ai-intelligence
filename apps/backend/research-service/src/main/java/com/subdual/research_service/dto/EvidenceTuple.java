package com.subdual.research_service.dto;

import com.subdual.research_service.domain.ConfidenceTier;
import java.util.List;

public record EvidenceTuple(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        ConfidenceTier confidence,
        List<String> corroboratingSources,
        boolean conflictDetected
) {
    public EvidenceTuple(String value, String sourceUrl, String evidenceSnippet, ConfidenceTier confidence) {
        this(
                value,
                sourceUrl,
                evidenceSnippet,
                confidence,
                sourceUrl != null && !sourceUrl.isBlank() ? List.of(sourceUrl) : List.of(),
                false
        );
    }
}
