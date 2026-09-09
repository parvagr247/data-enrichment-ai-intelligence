package com.subdual.ai_intelligent_service.requirement.model;

import java.util.List;

/**
 * Complete, structured enrichment execution plan.
 * Clearly specifies what fields are needed, existing, research-based, or AI-synthesized.
 */
public record EnrichmentPlan(
        String entityType,
        String requestedObjective,
        boolean isDefaultPlan,
        List<PlannedField> plannedFields,
        List<String> neededFields,
        List<String> existingFields,
        List<String> researchFields,
        List<String> aiFields,
        List<String> localFields,
        List<String> searchKeywords,
        List<String> validationNotes
) {
}
