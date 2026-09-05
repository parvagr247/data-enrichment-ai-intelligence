package com.subdual.ai_intelligent_service.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request payload for structured fact extraction from cleaned text.
 */
public record ExtractionRequest(
        @NotBlank(message = "Field 'entityName' must not be blank")
        String entityName,

        String entityType,

        @NotBlank(message = "Field 'sourceUrl' must not be blank")
        String sourceUrl,

        @NotBlank(message = "Field 'textContent' must not be blank")
        String textContent,

        List<String> targetFields
) {
    public ExtractionRequest {
        if (targetFields == null || targetFields.isEmpty()) {
            targetFields = List.of("role", "organization", "description", "summary", "headquarters", "technologies");
        }
    }
}
