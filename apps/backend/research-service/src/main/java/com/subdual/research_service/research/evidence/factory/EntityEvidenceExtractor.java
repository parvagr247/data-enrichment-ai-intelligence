package com.subdual.research_service.research.evidence.factory;

import java.util.List;
import java.util.Map;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.research.evidence.document.ExtractedDocument;
import com.subdual.research_service.research.evidence.support.EntityResolver;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;

/**
 * Strategy interface for extracting evidence for a specific EntityType.
 */
public interface EntityEvidenceExtractor {
    boolean supports(EntityType entityType);
    void extract(
            ResearchTarget target,
            List<ExtractedDocument> documents,
            Map<String, EvidenceTuple> attributes,
            Map<String, EntityResolver.ResolutionResult> resolutions
    );
}
