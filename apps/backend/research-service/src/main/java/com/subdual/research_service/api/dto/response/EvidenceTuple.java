package com.subdual.research_service.api.dto.response;

import com.subdual.research_service.research.model.ConfidenceTier;
import java.util.List;

public record EvidenceTuple(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        ConfidenceTier confidence,
        List<String> corroboratingSources,
        boolean conflictDetected,
        String conflictDescription,
        String sourceType,
        String extractionMethod
) {
    public EvidenceTuple(
            String value,
            String sourceUrl,
            String evidenceSnippet,
            ConfidenceTier confidence,
            List<String> corroboratingSources,
            boolean conflictDetected
    ) {
        this(value, sourceUrl, evidenceSnippet, confidence, corroboratingSources, conflictDetected, null, null, null);
    }

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
