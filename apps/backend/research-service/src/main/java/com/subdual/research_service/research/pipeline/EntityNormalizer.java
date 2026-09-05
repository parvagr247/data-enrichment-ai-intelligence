package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.research.model.ResearchTarget;

public interface EntityNormalizer {

    ResearchTarget normalize(ResearchRequest request);
}
