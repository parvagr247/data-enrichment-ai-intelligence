package com.subdual.ai_intelligent_service.requirement.service;

import com.subdual.ai_intelligent_service.requirement.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.requirement.model.FieldSourceStrategy;
import com.subdual.ai_intelligent_service.requirement.model.PlannedField;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generates intelligent default enrichment plans when no explicit requirement is provided (Task 33).
 */
@Component
@lombok.RequiredArgsConstructor
public class DefaultPlanGenerator {

    private final com.subdual.ai_intelligent_service.requirement.normalizer.RequirementNormalizer requirementNormalizer;

    private static final Map<String, List<String>> DEFAULT_FIELDS_BY_TYPE = Map.of(
            "PERSON", List.of("role", "organization", "location", "education", "summary"),
            "ORGANIZATION", List.of("industry", "location", "founders", "overview", "funding"),
            "REPOSITORY", List.of("primary_language", "license", "tech_stack", "stars"),
            "PRODUCT", List.of("vendor", "category", "pricing_model", "overview"),
            "WEBSITE", List.of("overview", "topics", "primary_language"),
            "OTHER", List.of("summary", "keywords", "category")
    );

    public EnrichmentPlan generateDefaultPlan(String entityType, List<String> existingDatasetColumns) {
        String cleanType = (entityType != null && !entityType.isBlank())
                ? entityType.trim().toUpperCase(Locale.ROOT)
                : "PERSON";

        List<String> baselineFields = DEFAULT_FIELDS_BY_TYPE.getOrDefault(cleanType, DEFAULT_FIELDS_BY_TYPE.get("OTHER"));
        Set<String> normalizedExisting = new HashSet<>();
        if (existingDatasetColumns != null) {
            for (String col : existingDatasetColumns) {
                if (col != null && !col.isBlank()) {
                    normalizedExisting.add(requirementNormalizer.normalizeFieldKey(col));
                    normalizedExisting.add(col.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", ""));
                }
            }
        }

        List<PlannedField> plannedFields = new ArrayList<>();
        List<String> neededFields = new ArrayList<>();
        List<String> existingFields = new ArrayList<>();
        List<String> researchFields = new ArrayList<>();
        List<String> aiFields = new ArrayList<>();
        List<String> localFields = new ArrayList<>();
        List<String> searchKeywords = new ArrayList<>();

        for (String field : baselineFields) {
            boolean alreadyPresent = normalizedExisting.contains(field)
                    || normalizedExisting.stream().anyMatch(ex -> ex.contains(field) || field.contains(ex));

            if (alreadyPresent) {
                existingFields.add(field);
                plannedFields.add(new PlannedField(
                        field,
                        cleanType,
                        FieldSourceStrategy.EXISTING_DATASET_FIELD,
                        "Field already detected in input dataset columns"
                ));
            } else {
                neededFields.add(field);
                searchKeywords.add(field.replace("_", " "));

                if (field.equals("primary_language") || field.equals("license") || field.equals("stars")) {
                    researchFields.add(field);
                    plannedFields.add(new PlannedField(
                            field,
                            cleanType,
                            FieldSourceStrategy.RESEARCH_LOOKUP,
                            "Deterministic web document research"
                    ));
                } else {
                    aiFields.add(field);
                    plannedFields.add(new PlannedField(
                            field,
                            cleanType,
                            FieldSourceStrategy.AI_SYNTHESIS,
                            "Grounded AI reasoning from authoritative sources"
                    ));
                }
            }
        }

        return new EnrichmentPlan(
                cleanType,
                "Default comprehensive profile for " + cleanType,
                true,
                plannedFields,
                neededFields,
                existingFields,
                researchFields,
                aiFields,
                localFields,
                searchKeywords,
                List.of("Generated default enrichment plan based on entity type '" + cleanType + "'")
        );
    }
}
