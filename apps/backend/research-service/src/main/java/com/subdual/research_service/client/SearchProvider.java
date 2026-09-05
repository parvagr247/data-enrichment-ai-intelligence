package com.subdual.research_service.client;

import com.subdual.research_service.domain.DiscoveredSource;
import java.util.List;

public interface SearchProvider {
    List<DiscoveredSource> search(String query, int maxResults);
}
