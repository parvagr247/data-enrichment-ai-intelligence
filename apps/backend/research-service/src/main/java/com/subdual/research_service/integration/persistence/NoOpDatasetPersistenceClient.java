package com.subdual.research_service.integration.persistence;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Null Object implementation of DatasetPersistenceClient.
 * Used when persistence is disabled, unconfigured, or in standalone test environments.
 */
@Slf4j
public class NoOpDatasetPersistenceClient implements DatasetPersistenceClient {

    @Override
    public void persistEntity(ResearchTarget target, List<ResearchSource> sources, Map<String, EvidenceTuple> attributes) {
        log.debug("[Persistence: NOOP] Skipping snapshot persistence for entityId: '{}'",
                target != null ? target.entityId() : "unknown");
    }
}
