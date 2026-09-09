package com.subdual.ai_intelligent_service.extraction.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ExtractionRequest(
        @NotBlank(message = "Field 'entityName' must not be blank")
        String entityName,

        String entityType,

        @NotBlank(message = "Field 'sourceUrl' must not be blank")
        String sourceUrl,

        String textContent,

        List<String> targetFields
) {
    public ExtractionRequest {
        if (textContent == null) {
            textContent = "";
        }
        if (targetFields == null || targetFields.isEmpty()) {
            targetFields = List.of("role", "organization", "description", "summary", "headquarters", "technologies");
        }
    }
}
