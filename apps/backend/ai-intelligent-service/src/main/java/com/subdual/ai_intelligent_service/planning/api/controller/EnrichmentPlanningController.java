package com.subdual.ai_intelligent_service.planning.api.controller;

import com.subdual.ai_intelligent_service.facade.AiServiceFacade;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller delegating enrichment planning to the unified AiServiceFacade.
 */
@RestController
@RequestMapping("/api/v2/ai/requirements")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentPlanningController {

    private final AiServiceFacade aiFacade;

    @PostMapping(value = "/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrichmentPlan> createEnrichmentPlan(
            @RequestBody EnrichmentRequirement requirement
    ) {
        if (requirement == null) {
            requirement = new EnrichmentRequirement(null, "PERSON");
        }

        log.info("Generating V2 enrichment plan for objective: '{}' (entityType: '{}')",
                requirement.userObjective(), requirement.entityType());

        return ResponseEntity.ok(aiFacade.planEnrichment(requirement));
    }
}
