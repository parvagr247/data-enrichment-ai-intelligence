package com.subdual.dataset_service.dto;

import java.util.List;

public record FactEvidenceDto(
        String field,
        String value,
        String sourceUrl,
        String evidenceSnippet,
        String confidence,
        List<String> corroboratingSources,
        boolean conflictDetected
) {
    public FactEvidenceDto {
        if (corroboratingSources == null) {
            corroboratingSources = List.of();
        }
    }
}
