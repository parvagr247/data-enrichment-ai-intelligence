package com.subdual.research_service.extraction.factory;

import com.subdual.research_service.research.model.EntityType;

import java.util.Optional;

/**
 * Factory for retrieving the appropriate EntityEvidenceExtractor strategy.
 */
public interface EntityEvidenceExtractorFactory {
    Optional<EntityEvidenceExtractor> getExtractor(EntityType entityType);
}
