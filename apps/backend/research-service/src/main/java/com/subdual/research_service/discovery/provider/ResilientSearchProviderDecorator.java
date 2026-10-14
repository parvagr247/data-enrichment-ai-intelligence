package com.subdual.research_service.discovery.provider;

import com.subdual.research_service.common.exception.ExternalServiceException;
import com.subdual.research_service.research.model.DiscoveredSource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * GoF Decorator Pattern: Decorates a primary SearchProvider with automatic
 * resilience fallback and execution telemetry.
 * If the primary search provider encounters an external failure or quota exhaustion,
 * it seamlessly delegates to the fallback provider without failing the research pipeline.
 */
@Slf4j
public class ResilientSearchProviderDecorator implements SearchProvider {

    private final SearchProvider primary;
    private final SearchProvider fallback;

    public ResilientSearchProviderDecorator(SearchProvider primary, SearchProvider fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public List<DiscoveredSource> search(String query, int maxResults) {
        try {
            List<DiscoveredSource> results = primary.search(query, maxResults);
            if (results != null && !results.isEmpty()) {
                return results;
            }
            log.info("[Decorator: EMPTY_PRIMARY] Primary provider '{}' returned 0 results for '{}'; consulting fallback '{}'",
                    primary.providerName(), query, fallback.providerName());
            return fallback.search(query, maxResults);
        } catch (ExternalServiceException ex) {
            log.warn("[Decorator: FALLBACK_ENGAGED] Primary search provider '{}' failed for query '{}': {}. Falling back to '{}'",
                    primary.providerName(), query, ex.getMessage(), fallback.providerName());
            return fallback.search(query, maxResults);
        } catch (Exception ex) {
            log.warn("[Decorator: FALLBACK_ENGAGED] Unexpected error from primary provider '{}': {}. Falling back to '{}'",
                    primary.providerName(), ex.getMessage(), fallback.providerName());
            return fallback.search(query, maxResults);
        }
    }

    @Override
    public String providerName() {
        return primary.providerName() + "+fallback";
    }
}
