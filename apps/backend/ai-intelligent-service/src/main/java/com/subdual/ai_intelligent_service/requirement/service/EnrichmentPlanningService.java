package com.subdual.ai_intelligent_service.requirement.service;

import com.subdual.ai_intelligent_service.requirement.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.requirement.model.EnrichmentRequirement;

/**
 * Core interface for requirement understanding and enrichment planning (Tasks 31-40).
 */
public interface EnrichmentPlanningService {

    EnrichmentPlan planEnrichment(EnrichmentRequirement requirement);
}
