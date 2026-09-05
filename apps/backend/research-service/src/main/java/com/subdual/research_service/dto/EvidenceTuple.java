package com.subdual.research_service.dto;

import com.subdual.research_service.domain.ConfidenceTier;

public record EvidenceTuple(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        ConfidenceTier confidence
) {}
