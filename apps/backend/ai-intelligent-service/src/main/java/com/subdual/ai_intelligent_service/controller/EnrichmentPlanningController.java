package com.subdual.ai_intelligent_service.controller;

import com.subdual.ai_intelligent_service.requirement.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.requirement.model.EnrichmentRequirement;
import com.subdual.ai_intelligent_service.requirement.service.EnrichmentPlanningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 Controller for requirement understanding and enrichment planning (Tasks 31-40).
 * Thin controller adhering to Rule 2.
 */
@RestController
@RequestMapping("/api/v2/ai/requirements")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentPlanningController {

    private final EnrichmentPlanningService planningService;

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrichmentPlan> createEnrichmentPlan(
            @RequestBody EnrichmentRequirement requirement
    ) {
        if (requirement == null) {
            requirement = new EnrichmentRequirement(null, "PERSON");
        }

        log.info("Generating V2 enrichment plan for objective: '{}' (entityType: '{}')",
                requirement.userObjective(), requirement.entityType());

        EnrichmentPlan plan = planningService.planEnrichment(requirement);
        return ResponseEntity.ok(plan);
    }
}
