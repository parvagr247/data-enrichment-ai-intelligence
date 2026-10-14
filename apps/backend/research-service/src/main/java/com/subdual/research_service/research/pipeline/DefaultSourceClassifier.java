package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.discovery.ranking.SourceTypeClassifier;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.model.SourceType;
import org.springframework.stereotype.Component;

/**
 * Clean component for classifying source types based on URL domain patterns.
 */
@Component
public class DefaultSourceClassifier implements SourceClassifier {

    @Override
    public String classify(String url, String title, String candidateType, ResearchTarget target) {
        String targetCanonical = target != null ? target.canonicalUrl() : null;
        SourceType type = SourceTypeClassifier.classify(url, targetCanonical);
        return type != null ? type.name() : "OTHER";
    }
}
