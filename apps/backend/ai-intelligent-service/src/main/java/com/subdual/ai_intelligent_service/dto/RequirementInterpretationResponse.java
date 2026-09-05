package com.subdual.ai_intelligent_service.dto;

import java.util.List;

public record RequirementInterpretationResponse(
        List<String> requestedFields,
        String scopeDescription,
        boolean isDefaultScope
) {
    public RequirementInterpretationResponse {
        if (requestedFields == null) {
            requestedFields = List.of();
        }
    }
}
