package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchTarget;

public interface RelevanceEvaluator {

    double evaluateRelevance(DiscoveredSource source, String classifiedType, ResearchTarget target);

    double evaluateRelevance(double providerRelevance, String sourceType, String url, String title, ResearchTarget target);

    default double evaluateRelevance(double providerScore, String sourceType, ResearchTarget target) {
        return evaluateRelevance(providerScore, sourceType, null, null, target);
    }
}
