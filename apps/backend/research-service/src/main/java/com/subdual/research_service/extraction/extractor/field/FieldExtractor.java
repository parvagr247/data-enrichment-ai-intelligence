package com.subdual.research_service.extraction.extractor.field;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;

/**
 * Reusable contract for field-specific deterministic extraction (Task 62).
 */
public interface FieldExtractor {

    String fieldKey();

    boolean supports(String requestedField, EntityType entityType);

    EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target);
}
