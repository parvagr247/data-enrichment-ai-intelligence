package com.subdual.research_service.discovery.model;

/**
 * Encapsulates a search query with its classification and strategy.
 */
public record ResearchQuery(
        String queryText,
        QueryIntent intent,
        QueryStrategy strategy
) {
    public ResearchQuery(String queryText) {
        this(queryText, QueryIntent.GENERAL_PROFILE, QueryStrategy.GENERAL_DISCOVERY);
    }
}
