package com.subdual.research_service.integration.persistence;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Resilient implementation of ResearchSnapshotPersister.
 * Guarantees that persistence failures (e.g. database down, network timeout)
 * record diagnostics warnings and never fail an otherwise successful research execution.
 */
@Component
public class DefaultResearchSnapshotPersister implements ResearchSnapshotPersister {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchSnapshotPersister.class);

    private final DatasetPersistenceClient persistenceClient;

    @Autowired
    public DefaultResearchSnapshotPersister(DatasetPersistenceClient persistenceClient) {
        this.persistenceClient = persistenceClient != null ? persistenceClient : new NoOpDatasetPersistenceClient();
    }

    public DefaultResearchSnapshotPersister() {
        this(new NoOpDatasetPersistenceClient());
    }

    @Override
    public void persistSnapshot(
            ResearchTarget target,
            List<ResearchSource> sources,
            Map<String, EvidenceTuple> attributes,
            ResearchDiagnostics diagnostics
    ) {
        try {
            persistenceClient.persistEntity(target, sources, attributes);
        } catch (Exception ex) {
            log.warn("[Pipeline: PERSISTENCE_FAILED] Non-blocking dataset persistence failed: {}", ex.getMessage());
            if (diagnostics != null) {
                diagnostics.recordPersistenceFailure(ex.getMessage());
            }
        }
    }
}
