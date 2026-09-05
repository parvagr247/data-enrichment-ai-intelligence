package com.subdual.research_service.client.dto;

public record EntityAttributeDto(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        String confidence
) {}
