package com.subdual.research_service.service;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.ResearchTarget;

/**
 * Strategy interface for evaluating the relevance and confidence score of discovered sources.
 * Can be replaced by embeddings, reranking models, or LLM evaluation in later stages.
 */
public interface RelevanceEvaluator {

    /**
     * Evaluates source relevance relative to the entity target.
     *
     * @param source         the raw or normalized discovered source
     * @param classifiedType the classified source type
     * @param target         the research target
     * @return a normalized score between 0.00 and 1.00
     */
    double evaluateRelevance(DiscoveredSource source, String classifiedType, ResearchTarget target);

    double evaluateRelevance(double providerRelevance, String sourceType, String url, String title, ResearchTarget target);
}
