package com.subdual.dataset_service.dataset.model;

public record MalformedRow(
        int rowIndex,
        String rawContent,
        String reason
) {
}
