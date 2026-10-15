package com.subdual.dataset_service.shared.dto;

public record EntityAttributeDto(
        String value,
        String sourceUrl,
        String evidenceSnippet,
        String confidence
) {}
