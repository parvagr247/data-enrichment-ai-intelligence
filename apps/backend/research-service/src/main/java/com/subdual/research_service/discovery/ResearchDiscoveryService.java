package com.subdual.research_service.discovery;

import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.ResearchTarget;
import java.util.List;

/**
 * High-level application service responsible for query building and source discovery.
 */
public interface ResearchDiscoveryService {

    /**
     * Builds discovery queries and retrieves candidate sources for the target entity.
     *
     * @param target the normalized research target
     * @return list of discovered candidate sources
     */
    List<DiscoveredSource> discoverSources(ResearchTarget target);
}
