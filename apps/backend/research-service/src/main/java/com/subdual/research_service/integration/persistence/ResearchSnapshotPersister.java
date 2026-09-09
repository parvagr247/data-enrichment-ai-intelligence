package com.subdual.research_service.integration.persistence;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;

import java.util.List;
import java.util.Map;

public interface ResearchSnapshotPersister {

    void persistSnapshot(
            ResearchTarget target,
            List<ResearchSource> sources,
            Map<String, EvidenceTuple> attributes,
            ResearchDiagnostics diagnostics
    );
}
