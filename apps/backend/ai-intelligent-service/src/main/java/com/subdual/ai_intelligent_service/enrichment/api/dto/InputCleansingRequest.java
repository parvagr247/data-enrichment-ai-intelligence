package com.subdual.ai_intelligent_service.enrichment.api.dto;

import java.util.Map;

public record InputCleansingRequest(
        Map<String, String> rawInput,
        String entityType
) {}
