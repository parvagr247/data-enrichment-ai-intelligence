package com.subdual.ai_intelligent_service.requirement.service;

import com.subdual.ai_intelligent_service.requirement.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.requirement.model.EnrichmentRequirement;
import com.subdual.ai_intelligent_service.requirement.model.FieldSourceStrategy;
import com.subdual.ai_intelligent_service.requirement.model.PlannedField;
import com.subdual.ai_intelligent_service.requirement.normalizer.RequirementNormalizer;
import com.subdual.ai_intelligent_service.requirement.validator.RequirementValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Production implementation of requirement understanding and enrichment planning (Tasks 31-40).
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

    @Override
    public EnrichmentPlan planEnrichment(EnrichmentRequirement requirement) {
        String entityType = (requirement.entityType() != null && !requirement.entityType().isBlank())
                ? requirement.entityType().trim().toUpperCase(Locale.ROOT)
                : "PERSON";

        // 1. Task 33: Blank requirement generates intelligent default plan
        if (requirement.isBlankRequirement()) {
            log.info("Enrichment requirement is blank; generating default plan for entity type '{}'", entityType);
            return defaultPlanGenerator.generateDefaultPlan(entityType, requirement.existingDatasetColumns());
        }

        List<String> validationNotes = new ArrayList<>();
        Set<String> candidateFields = new LinkedHashSet<>();

        // 2. Explicit fields if provided directly
        if (requirement.explicitFields() != null && !requirement.explicitFields().isEmpty()) {
            for (String f : requirement.explicitFields()) {
                candidateFields.add(requirementNormalizer.normalizeFieldKey(f));
            }
        }

        // 3. Natural-language requirement extraction (deterministic first, Spring AI second)
        Set<String> extractedFromText = requirementNormalizer.extractNormalizedFieldsFromText(requirement.userObjective());
        candidateFields.addAll(extractedFromText);

        // If user provided a complex phrase that wasn't in the static dictionary, try Spring AI
        if (candidateFields.isEmpty() && chatModel != null) {
            try {
                String aiPrompt = "You are a data enrichment planner. Given this user requirement: \""
                        + requirement.userObjective()
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

        // If still empty, fall back to default plan for entity type
        if (candidateFields.isEmpty()) {
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

        // 4. Task 36: Validate and filter fields
        List<String> validatedFields = requirementValidator.validateAndFilterFields(candidateFields, validationNotes);

        // 5. Partition into existing vs needed vs research vs AI
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
            boolean alreadyPresent = false;
            for (String ex : existingLower) {
                if (ex.contains(field) || field.contains(ex)) {
                    alreadyPresent = true;
                    break;
                }
            }

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
}
