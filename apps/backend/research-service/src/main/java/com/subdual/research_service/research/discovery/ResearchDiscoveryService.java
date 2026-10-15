package com.subdual.research_service.research.discovery;

import java.util.List;

import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchTarget;

public interface ResearchDiscoveryService {

    List<DiscoveredSource> discoverSources(ResearchTarget target);

    // Defines a contract for secondary, targeted web search discovery aimed at closing information gaps during entity research.
    List<DiscoveredSource> discoverAdaptiveSources(ResearchTarget target, List<String> missingFields, int maxResults);
}
