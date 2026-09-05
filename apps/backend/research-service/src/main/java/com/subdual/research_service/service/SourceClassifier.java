package com.subdual.research_service.service;

import com.subdual.research_service.domain.ResearchTarget;

/**
 * Strategy interface for classifying discovered research sources.
 * Can be replaced or extended in future stages with ML or LLM-based classifiers.
 */
public interface SourceClassifier {

    /**
     * Classifies a discovered URL and metadata into a standard source category.
     *
     * @param url           the source URL
     * @param title         the page title or headline (may be null)
     * @param candidateType any initial type suggested by provider or client
     * @param target        the research entity target
     * @return a standardized source category (e.g., OFFICIAL_WEBSITE, DOCUMENTATION, SOCIAL_PROFILE, NEWS, SEARCH_RESULT, UNKNOWN)
     */
    String classify(String url, String title, String candidateType, ResearchTarget target);

    default String classify(String url, String candidateType, ResearchTarget target) {
        return classify(url, null, candidateType, target);
    }
}
