package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.research.model.ResearchTarget;

/**
 * Normalizes input research requests into canonical research targets with
 * computed identity identifiers, standardized URLs, and sanitized display names.
 */
public interface EntityNormalizer {

    ResearchTarget normalize(ResearchRequest request);
}
