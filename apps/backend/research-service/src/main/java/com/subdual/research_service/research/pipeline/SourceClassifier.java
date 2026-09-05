package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.ResearchTarget;

/**
 * Strategy interface for classifying discovered research sources.
 */
public interface SourceClassifier {

    /**
     * Classifies a discovered URL and metadata into a standard source category.
     */
    String classify(String url, String title, String candidateType, ResearchTarget target);

    default String classify(String url, String candidateType, ResearchTarget target) {
        return classify(url, null, candidateType, target);
    }
}
