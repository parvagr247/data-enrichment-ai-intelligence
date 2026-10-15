package com.subdual.research_service.research.evidence.factory;

import java.util.Optional;

import com.subdual.research_service.research.model.EntityType;

/**
 * Factory for retrieving the appropriate EntityEvidenceExtractor strategy.
 */
public interface EntityEvidenceExtractorFactory {
    Optional<EntityEvidenceExtractor> getExtractor(EntityType entityType);
}
