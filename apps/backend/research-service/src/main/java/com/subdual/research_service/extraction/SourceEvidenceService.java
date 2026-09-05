package com.subdual.research_service.extraction;

import com.subdual.research_service.diagnostics.ResearchDiagnostics;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;

import java.util.List;
import java.util.Map;

/**
 * Service responsible for processing ranked sources, fetching content,
 * resolving entities against documents, and extracting grounded evidence tuples.
 */
public interface SourceEvidenceService {

    Map<String, EvidenceTuple> extractEvidence(
            ResearchTarget target,
            List<ResearchSource> rankedSources,
            ResearchDiagnostics diagnostics
    );
}
