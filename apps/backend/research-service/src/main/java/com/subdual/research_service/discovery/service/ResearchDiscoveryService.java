package com.subdual.research_service.discovery.service;

import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchTarget;
import java.util.List;

public interface ResearchDiscoveryService {

    List<DiscoveredSource> discoverSources(ResearchTarget target);

    // Defines a contract for secondary, targeted web search discovery aimed at closing information gaps during entity research.
    List<DiscoveredSource> discoverAdaptiveSources(ResearchTarget target, List<String> missingFields, int maxResults);
}
