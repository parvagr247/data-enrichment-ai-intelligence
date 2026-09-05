package com.subdual.research_service.discovery.provider;

import com.subdual.research_service.research.model.DiscoveredSource;
import java.util.List;


public interface SearchProvider {

    List<DiscoveredSource> search(String query, int maxResults);
}
