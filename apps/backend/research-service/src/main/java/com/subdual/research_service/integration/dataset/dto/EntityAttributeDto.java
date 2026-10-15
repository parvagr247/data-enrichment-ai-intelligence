package com.subdual.research_service.integration.dataset.dto;

public record EntityAttributeDto(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        String confidence
) {}
