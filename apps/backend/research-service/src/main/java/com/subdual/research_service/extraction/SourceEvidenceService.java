package com.subdual.research_service.extraction;

import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.api.dto.EvidenceTuple;

import java.util.List;
import java.util.Map;

public interface SourceEvidenceService {

    Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> rankedSources,
            ResearchDiagnostics diagnostics );
            
}
