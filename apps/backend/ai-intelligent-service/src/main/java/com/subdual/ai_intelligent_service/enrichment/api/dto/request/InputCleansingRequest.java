package com.subdual.ai_intelligent_service.enrichment.api.dto.request;

import java.util.Map;

public record InputCleansingRequest(
        Map<String, String> rawInput,
        String entityType
) {}
