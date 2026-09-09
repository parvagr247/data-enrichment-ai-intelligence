package com.subdual.ai_intelligent_service.enrichment.api.dto;

import java.util.Map;

public record RequirementInterpretationRequest(
        String requirement,
        String entityType,
        Map<String, String> rawInput
) {}
