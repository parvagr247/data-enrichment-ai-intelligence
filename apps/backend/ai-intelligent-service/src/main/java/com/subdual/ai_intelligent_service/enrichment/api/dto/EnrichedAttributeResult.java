package com.subdual.ai_intelligent_service.enrichment.api.dto;

import java.util.List;

public record EnrichedAttributeResult(
        String field,
        String value,
        String originalValue,
        String confidence,
        String status,
        List<String> sources,
        String evidence,
        String notes
) {
    public EnrichedAttributeResult {
        if (sources == null) {
            sources = List.of();
        }
    }
}
