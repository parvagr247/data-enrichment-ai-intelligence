package com.subdual.research_service.integration.dataset;

import java.util.List;
import java.util.Map;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;

public interface DatasetPersistenceClient {
    void persistEntity(ResearchTarget target, List<ResearchSource> sources, Map<String, EvidenceTuple> attributes);
}
