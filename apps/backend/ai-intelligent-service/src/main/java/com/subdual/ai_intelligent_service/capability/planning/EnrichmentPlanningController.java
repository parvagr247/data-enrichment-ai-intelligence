package com.subdual.ai_intelligent_service.capability.planning;

import com.subdual.ai_intelligent_service.capability.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.capability.planning.model.EnrichmentRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v2/ai/requirements")
@RequiredArgsConstructor
public class EnrichmentPlanningController {

    private final EnrichmentPlanningService planningService;

    @PostMapping("/plan")
    public ResponseEntity<EnrichmentPlan> createEnrichmentPlan(
            @RequestBody EnrichmentRequirement requirement
    ) {
        if (requirement == null) {
            requirement = new EnrichmentRequirement(null, "PERSON");
        }

        return ResponseEntity.ok(planningService.planEnrichment(requirement));
    }
}
