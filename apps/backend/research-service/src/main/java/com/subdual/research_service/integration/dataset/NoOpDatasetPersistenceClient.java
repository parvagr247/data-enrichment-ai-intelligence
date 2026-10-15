package com.subdual.research_service.integration.dataset;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;

/**
 * Null Object implementation of DatasetPersistenceClient.
 * Used when persistence is disabled, unconfigured, or in standalone test environments.
 */
@Slf4j
public class NoOpDatasetPersistenceClient implements DatasetPersistenceClient {

    @Override // Safely ignores snapshot persistence in no-op environments.
    public void persistEntity(ResearchTarget target, List<ResearchSource> sources, Map<String, EvidenceTuple> attributes) {
        log.debug("[Persistence: NOOP] Skipping snapshot persistence for entityId: '{}'",
                target != null ? target.entityId() : "unknown");
    }
}
