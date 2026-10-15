package com.subdual.research_service.research.evidence;

import java.util.List;
import java.util.Map;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;

public interface SourceEvidenceService {

    Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> rankedSources,
            ResearchDiagnostics diagnostics );

    default void applyTargetFields(ResearchTarget target, Map<String, EvidenceTuple> attributes) {}
}
