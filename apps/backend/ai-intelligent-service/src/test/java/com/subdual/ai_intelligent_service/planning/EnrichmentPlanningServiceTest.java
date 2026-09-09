package com.subdual.ai_intelligent_service.planning;

import com.subdual.ai_intelligent_service.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentRequirement;
import com.subdual.ai_intelligent_service.planning.model.FieldSourceStrategy;
import com.subdual.ai_intelligent_service.planning.service.helper.DefaultPlanGenerator;
import com.subdual.ai_intelligent_service.planning.service.helper.RequirementNormalizer;
import com.subdual.ai_intelligent_service.planning.service.helper.RequirementValidator;
import com.subdual.ai_intelligent_service.planning.service.impl.DefaultEnrichmentPlanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentPlanningServiceTest {

    private DefaultEnrichmentPlanningService planningService;

    @BeforeEach
    void setUp() {
        RequirementNormalizer normalizer = new RequirementNormalizer();
        DefaultPlanGenerator planGenerator = new DefaultPlanGenerator(normalizer);
        RequirementValidator validator = new RequirementValidator();
        planningService = new DefaultEnrichmentPlanningService(planGenerator, normalizer, validator);
    }

    @Test
    @DisplayName("Should generate sensible default enrichment plan when requirement is blank")
    void shouldGenerateDefaultPlanWhenBlank() {
        EnrichmentRequirement blankReq = new EnrichmentRequirement(
                "",
                "PERSON",
                List.of("FullName", "Company"),
                List.of()
        );

        EnrichmentPlan plan = planningService.planEnrichment(blankReq);

        assertNotNull(plan);
        assertTrue(plan.isDefaultPlan());
        assertEquals("PERSON", plan.entityType());

        // 'organization' was already detected in input columns ('Company')
        assertTrue(plan.existingFields().contains("organization"));

        // Remaining needed fields should be scheduled for research/AI
        assertTrue(plan.neededFields().contains("role"));
        assertTrue(plan.neededFields().contains("location"));
        assertTrue(plan.neededFields().contains("education"));

        assertFalse(plan.searchKeywords().isEmpty());
    }

    @Test
    @DisplayName("Should parse natural-language requirement and map to normalized fields")
    void shouldParseNaturalLanguageRequirement() {
        EnrichmentRequirement req = new EnrichmentRequirement(
                "Find current job title, employer, and where they studied",
                "PERSON",
                List.of(),
                List.of()
        );

        EnrichmentPlan plan = planningService.planEnrichment(req);

        assertNotNull(plan);
        assertFalse(plan.isDefaultPlan());

        // Check canonical field normalization:
        // "job title" -> role
        // "employer" -> organization
        // "where they studied" -> education
        assertTrue(plan.neededFields().contains("role"));
        assertTrue(plan.neededFields().contains("organization"));
        assertTrue(plan.neededFields().contains("education"));

        // Verify planned fields have AI_SYNTHESIS strategy
        boolean hasAiField = plan.plannedFields().stream()
                .anyMatch(f -> f.strategy() == FieldSourceStrategy.AI_SYNTHESIS);
        assertTrue(hasAiField);
    }

    @Test
    @DisplayName("Should validate requested fields and filter dangerous/disallowed tokens")
    void shouldFilterRestrictedFields() {
        EnrichmentRequirement req = new EnrichmentRequirement(
                "Find password, credit_card, role and location",
                "PERSON",
                List.of(),
                List.of("private_key", "skills")
        );

        EnrichmentPlan plan = planningService.planEnrichment(req);

        assertNotNull(plan);
        assertFalse(plan.neededFields().contains("password"));
        assertFalse(plan.neededFields().contains("credit_card"));
        assertFalse(plan.neededFields().contains("private_key"));

        assertTrue(plan.neededFields().contains("role"));
        assertTrue(plan.neededFields().contains("location"));
        assertTrue(plan.neededFields().contains("skills"));

        assertFalse(plan.validationNotes().isEmpty());
    }

    @Test
    @DisplayName("Should distinguish existing dataset fields from research targets")
    void shouldDistinguishExistingFields() {
        EnrichmentRequirement req = new EnrichmentRequirement(
                "Find current role, company and location",
                "PERSON",
                List.of("name", "organization"),
                List.of()
        );

        EnrichmentPlan plan = planningService.planEnrichment(req);

        assertTrue(plan.existingFields().contains("organization"));
        assertTrue(plan.neededFields().contains("role"));
        assertTrue(plan.neededFields().contains("location"));
    }
}
