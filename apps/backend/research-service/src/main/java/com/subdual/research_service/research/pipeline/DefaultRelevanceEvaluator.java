package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.discovery.model.QueryIntent;
import com.subdual.research_service.discovery.ranking.SourceRanker;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchTarget;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Clean component for evaluating relevance scores of candidate sources.
 */
@Component
public class DefaultRelevanceEvaluator implements RelevanceEvaluator {

    @Override
    public double evaluateRelevance(DiscoveredSource source, String classifiedType, ResearchTarget target) {
        if (source == null) return 0.50;
        List<DiscoveredSource> ranked = SourceRanker.rankSources(List.of(source), target, QueryIntent.GENERAL_PROFILE);
        return (!ranked.isEmpty() && ranked.get(0).relevance() != null) ? ranked.get(0).relevance() : 0.50;
    }

    @Override
    public double evaluateRelevance(double providerRelevance, String sourceType, String url, String title, ResearchTarget target) {
        DiscoveredSource dummy = new DiscoveredSource(url, title, sourceType, null, providerRelevance, null);
        return evaluateRelevance(dummy, sourceType, target);
    }
}
