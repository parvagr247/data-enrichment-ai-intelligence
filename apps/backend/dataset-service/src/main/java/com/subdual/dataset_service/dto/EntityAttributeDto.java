package com.subdual.dataset_service.dto;

public record EntityAttributeDto(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        String confidence
) {}
