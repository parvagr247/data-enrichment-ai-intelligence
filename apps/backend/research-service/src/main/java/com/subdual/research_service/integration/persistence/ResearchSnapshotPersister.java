package com.subdual.research_service.integration.persistence;

import java.util.List;
import java.util.Map;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;

public interface ResearchSnapshotPersister {

    void persistSnapshot(
            ResearchTarget target,
            List<ResearchSource> sources,
            Map<String, EvidenceTuple> attributes,
            ResearchDiagnostics diagnostics
    );
}
