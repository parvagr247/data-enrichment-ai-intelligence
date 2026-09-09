package com.subdual.research_service.integration.persistence;

public record EntityAttributeDto(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        String confidence
) {}
