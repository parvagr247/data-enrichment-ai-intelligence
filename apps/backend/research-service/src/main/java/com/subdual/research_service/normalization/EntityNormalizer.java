package com.subdual.research_service.normalization;

import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.request.ResearchRequest;

/**
 * Normalizes input research requests into canonical research targets with
 * computed identity identifiers, standardized URLs, and sanitized display names.
 */
public interface EntityNormalizer {

    ResearchTarget normalize(ResearchRequest request);
}
