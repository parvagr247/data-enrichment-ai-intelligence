package com.subdual.ai_intelligent_service.capability.planning;

import com.subdual.ai_intelligent_service.capability.planning.model.EnrichmentPlan;
import com.subdual.ai_intelligent_service.capability.planning.model.EnrichmentRequirement;

/**
 * Core interface for requirement understanding and enrichment planning.
 */
public interface EnrichmentPlanningService {

    EnrichmentPlan planEnrichment(EnrichmentRequirement requirement);
}
