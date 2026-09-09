package com.subdual.ai_intelligent_service.planning.service;

import com.subdual.ai_intelligent_service.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.planning.model.EnrichmentRequirement;

/**
 * Core interface for requirement understanding and enrichment planning.
 */
public interface EnrichmentPlanningService {

    EnrichmentPlan planEnrichment(EnrichmentRequirement requirement);
}
