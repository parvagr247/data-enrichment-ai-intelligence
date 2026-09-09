package com.subdual.dataset_service.ingestion.model;

/**
 * Details of a malformed row detected during dataset parsing.
 */
public record MalformedRow(
        int rowIndex,
        String rawContent,
        String reason
) {
}
