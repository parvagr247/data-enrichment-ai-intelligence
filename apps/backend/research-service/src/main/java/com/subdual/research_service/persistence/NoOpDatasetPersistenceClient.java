package com.subdual.research_service.persistence;

import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Null Object implementation of DatasetPersistenceClient.
 * Used when persistence is disabled, unconfigured, or in standalone test environments.
 */
public class NoOpDatasetPersistenceClient implements DatasetPersistenceClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpDatasetPersistenceClient.class);

    @Override
    public void persistEntity(ResearchTarget target, List<ResearchSource> sources, Map<String, EvidenceTuple> attributes) {
        log.debug("[Persistence: NOOP] Skipping snapshot persistence for entityId: '{}'",
                target != null ? target.entityId() : "unknown");
    }
}
