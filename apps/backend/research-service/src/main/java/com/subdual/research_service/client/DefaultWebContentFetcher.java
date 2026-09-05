package com.subdual.research_service.client;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.WebFetchProperties;

/**
 * Backward-compatible subclass. Prefer com.subdual.research_service.source.DefaultWebContentFetcher.
 */
@Deprecated
public class DefaultWebContentFetcher extends com.subdual.research_service.source.DefaultWebContentFetcher implements WebContentFetcher {

    public DefaultWebContentFetcher(WebFetchProperties properties) {
        super(properties);
    }

    public DefaultWebContentFetcher(WebFetchProperties properties, ResearchDiscoveryProperties discoveryProperties) {
        super(properties, discoveryProperties);
    }

    public DefaultWebContentFetcher(WebFetchProperties properties, boolean mockMode) {
        super(properties, mockMode);
    }
}
