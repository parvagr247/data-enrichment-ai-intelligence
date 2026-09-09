package com.subdual.ai_intelligent_service.planning.service.impl;

import com.subdual.ai_intelligent_service.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentRequirement;
import com.subdual.ai_intelligent_service.planning.model.FieldSourceStrategy;
import com.subdual.ai_intelligent_service.planning.model.PlannedField;
import com.subdual.ai_intelligent_service.planning.service.EnrichmentPlanningService;
import com.subdual.ai_intelligent_service.planning.service.helper.DefaultPlanGenerator;
import com.subdual.ai_intelligent_service.planning.service.helper.RequirementNormalizer;
import com.subdual.ai_intelligent_service.planning.service.helper.RequirementValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Production implementation of requirement understanding and enrichment planning.
 * Decoupled from research execution: AI determines WHAT is needed, research determines HOW to find it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultEnrichmentPlanningService implements EnrichmentPlanningService {

    private final DefaultPlanGenerator defaultPlanGenerator;
    private final RequirementNormalizer requirementNormalizer;
    private final RequirementValidator requirementValidator;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Override // Constructs a structured enrichment plan based on user requirements and input schema.
    public EnrichmentPlan planEnrichment(EnrichmentRequirement requirement) {
        String entityType = resolveEntityType(requirement);

        if (requirement.isBlankRequirement()) {
            log.info("Enrichment requirement is blank; generating default plan for entity type '{}'", entityType);
            return defaultPlanGenerator.generateDefaultPlan(entityType, requirement.existingDatasetColumns());
        }

        List<String> validationNotes = new ArrayList<>();
        Set<String> candidateFields = extractCandidateFields(requirement, validationNotes);

        if (candidateFields.isEmpty()) {
            return buildFallbackPlan(requirement, entityType, validationNotes);
        }

        List<String> validatedFields = requirementValidator.validateAndFilterFields(candidateFields, validationNotes);
        return partitionAndBuildPlan(requirement, entityType, validatedFields, validationNotes);
    }

    private String resolveEntityType(EnrichmentRequirement requirement) {
        return (requirement != null && requirement.entityType() != null && !requirement.entityType().isBlank())
                ? requirement.entityType().trim().toUpperCase(Locale.ROOT)
                : "PERSON";
    }

    private Set<String> extractCandidateFields(EnrichmentRequirement requirement, List<String> validationNotes) {
        Set<String> candidateFields = new LinkedHashSet<>();

        if (requirement.explicitFields() != null && !requirement.explicitFields().isEmpty()) {
            for (String f : requirement.explicitFields()) {
                candidateFields.add(requirementNormalizer.normalizeFieldKey(f));
            }
        }

        Set<String> extractedFromText = requirementNormalizer.extractNormalizedFieldsFromText(requirement.userObjective());
        candidateFields.addAll(extractedFromText);

        if (candidateFields.isEmpty() && chatModel != null) {
            trySpringAiFieldExtraction(requirement.userObjective(), validationNotes, candidateFields);
        }

        return candidateFields;
    }

    private void trySpringAiFieldExtraction(String objective, List<String> validationNotes, Set<String> candidateFields) {
        try {
            String aiPrompt = "You are a data enrichment planner. Given this user requirement: \""
                    + objective
                    + "\", list the 1 to 5 target fields needed as comma-separated snake_case identifiers (e.g. role, organization, education). Output ONLY the comma-separated list.";
            String aiResponse = chatModel.call(aiPrompt);
            if (aiResponse != null && !aiResponse.isBlank()) {
                String[] tokens = aiResponse.split("[,;\\n]");
                for (String t : tokens) {
                    String clean = requirementNormalizer.normalizeFieldKey(t);
                    if (!clean.equalsIgnoreCase("UNKNOWN")) {
                        candidateFields.add(clean);
                    }
                }
                validationNotes.add("Parsed complex user requirement via Spring AI");
            }
        } catch (Exception ex) {
            log.warn("Spring AI requirement interpretation fallback triggered: {}", ex.getMessage());
            validationNotes.add("AI parsing failed (" + ex.getMessage() + "); using heuristic extraction");
        }
    }

    private EnrichmentPlan buildFallbackPlan(EnrichmentRequirement requirement, String entityType, List<String> validationNotes) {
        validationNotes.add("Could not derive specific fields from requirement; applied default plan");
        EnrichmentPlan fallback = defaultPlanGenerator.generateDefaultPlan(entityType, requirement.existingDatasetColumns());
        List<String> notes = new ArrayList<>(fallback.validationNotes());
        notes.addAll(validationNotes);
        return new EnrichmentPlan(
                fallback.entityType(),
                requirement.userObjective(),
                true,
                fallback.plannedFields(),
                fallback.neededFields(),
                fallback.existingFields(),
                fallback.researchFields(),
                fallback.aiFields(),
                fallback.localFields(),
                fallback.searchKeywords(),
                notes
        );
    }

    private EnrichmentPlan partitionAndBuildPlan(EnrichmentRequirement requirement, String entityType,
                                                  List<String> validatedFields, List<String> validationNotes) {
        Set<String> existingLower = new HashSet<>();
        if (requirement.existingDatasetColumns() != null) {
            requirement.existingDatasetColumns().forEach(c -> existingLower.add(c.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "")));
        }

        List<PlannedField> plannedFields = new ArrayList<>();
        List<String> neededFields = new ArrayList<>();
        List<String> existingFields = new ArrayList<>();
        List<String> researchFields = new ArrayList<>();
        List<String> aiFields = new ArrayList<>();
        List<String> localFields = new ArrayList<>();
        List<String> searchKeywords = new ArrayList<>();

        for (String field : validatedFields) {
            boolean alreadyPresent = existingLower.stream().anyMatch(ex -> ex.contains(field) || field.contains(ex));

            if (alreadyPresent) {
                existingFields.add(field);
                plannedFields.add(new PlannedField(
                        field,
                        entityType,
                        FieldSourceStrategy.EXISTING_DATASET_FIELD,
                        "Field already available in input dataset"
                ));
            } else {
                neededFields.add(field);
                searchKeywords.add(field.replace("_", " "));
                classifyFieldStrategy(field, entityType, researchFields, aiFields, plannedFields);
            }
        }

        return new EnrichmentPlan(
                entityType,
                requirement.userObjective(),
                false,
                plannedFields,
                neededFields,
                existingFields,
                researchFields,
                aiFields,
                localFields,
                searchKeywords,
                validationNotes
        );
    }

    private void classifyFieldStrategy(String field, String entityType, List<String> researchFields,
                                       List<String> aiFields, List<PlannedField> plannedFields) {
        if (field.equals("primary_language") || field.equals("license") || field.equals("stars")) {
            researchFields.add(field);
            plannedFields.add(new PlannedField(
                    field,
                    entityType,
                    FieldSourceStrategy.RESEARCH_LOOKUP,
                    "Deterministic web document lookup"
            ));
        } else {
            aiFields.add(field);
            plannedFields.add(new PlannedField(
                    field,
                    entityType,
                    FieldSourceStrategy.AI_SYNTHESIS,
                    "Grounded AI synthesis with exact supporting quotes"
            ));
        }
    }
}
