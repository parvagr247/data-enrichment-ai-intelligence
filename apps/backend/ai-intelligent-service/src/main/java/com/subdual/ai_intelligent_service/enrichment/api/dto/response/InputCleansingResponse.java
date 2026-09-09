package com.subdual.ai_intelligent_service.enrichment.api.dto.response;

import java.util.List;
import java.util.Map;

public record InputCleansingResponse(
        Map<String, String> cleanedInput,
        Map<String, String> normalizedFields,
        List<String> notes
) {
    public InputCleansingResponse {
        if (cleanedInput == null) {
            cleanedInput = Map.of();
        }
        if (normalizedFields == null) {
            normalizedFields = Map.of();
        }
        if (notes == null) {
            notes = List.of();
        }
    }
}
