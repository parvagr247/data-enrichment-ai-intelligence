package com.subdual.research_service.persistence;

import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;

import java.util.List;
import java.util.Map;

public interface DatasetPersistenceClient {
    void persistEntity(ResearchTarget target, List<ResearchSource> sources, Map<String, EvidenceTuple> attributes);
}
