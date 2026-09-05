package com.subdual.research_service.persistence;

import com.subdual.research_service.diagnostics.ResearchDiagnostics;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates the boundary for persisting research snapshots to downstream data stores.
 */
public interface ResearchSnapshotPersister {

    void persistSnapshot(
            ResearchTarget target,
            List<ResearchSource> sources,
            Map<String, EvidenceTuple> attributes,
            ResearchDiagnostics diagnostics
    );
}
