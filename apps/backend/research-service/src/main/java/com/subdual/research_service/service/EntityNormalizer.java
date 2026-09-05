package com.subdual.research_service.service;

import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.ResearchRequest;

/**
 * Normalizes inbound research entity requests into canonical domain representations.
 */
public interface EntityNormalizer {

    /**
     * Normalizes entity seed information into a canonical ResearchTarget.
     *
     * @param request the validated research request
     * @return canonical ResearchTarget
     */
    ResearchTarget normalize(ResearchRequest request);
}
