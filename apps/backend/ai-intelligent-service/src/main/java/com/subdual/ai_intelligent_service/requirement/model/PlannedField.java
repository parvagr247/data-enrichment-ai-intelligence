package com.subdual.ai_intelligent_service.requirement.model;

/**
 * A planned attribute in the enrichment plan (Tasks 31, 38).
 */
public record PlannedField(
        String fieldKey,
        String category,
        FieldSourceStrategy strategy,
        String justification
) {
}
