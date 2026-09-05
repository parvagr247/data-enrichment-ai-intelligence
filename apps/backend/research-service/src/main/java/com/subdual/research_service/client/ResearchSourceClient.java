package com.subdual.research_service.client;

import com.subdual.research_service.domain.DiscoveredSource;
import java.util.List;

public interface ResearchSourceClient {
    List<DiscoveredSource> discoverSources(String query, int maxResults);
}
