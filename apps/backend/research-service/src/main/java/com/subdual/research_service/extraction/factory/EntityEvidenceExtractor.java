package com.subdual.research_service.extraction.factory;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;

import java.util.List;
import java.util.Map;

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
