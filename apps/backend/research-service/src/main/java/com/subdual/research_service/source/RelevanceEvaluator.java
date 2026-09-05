package com.subdual.research_service.source;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.ResearchTarget;

/**
 * Strategy interface for scoring and ranking candidate sources relative to a target entity.
 */
public interface RelevanceEvaluator {

    double evaluateRelevance(DiscoveredSource source, String classifiedType, ResearchTarget target);

    double evaluateRelevance(double providerRelevance, String sourceType, String url, String title, ResearchTarget target);

    default double evaluateRelevance(double providerScore, String sourceType, ResearchTarget target) {
        return evaluateRelevance(providerScore, sourceType, null, null, target);
    }
}
