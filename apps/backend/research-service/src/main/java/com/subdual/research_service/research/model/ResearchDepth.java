package com.subdual.research_service.research.model;

public enum ResearchDepth {
    SHALLOW(3, 0),
    NORMAL(5, 1),
    DEEP(8, 2);

    private final int maxSources;
    private final int maxAdaptiveQueries;

    ResearchDepth(int maxSources, int maxAdaptiveQueries) {
        this.maxSources = maxSources;
        this.maxAdaptiveQueries = maxAdaptiveQueries;
    }

    public int maxSources() {
        return maxSources;
    }

    public int maxAdaptiveQueries() {
        return maxAdaptiveQueries;
    }
}
