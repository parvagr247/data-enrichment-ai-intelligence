package com.subdual.research_service.discovery.service;

import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.common.exception.ExternalServiceException;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.discovery.QueryBuilder;
import com.subdual.research_service.discovery.provider.SearchProvider;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultResearchDiscoveryService implements ResearchDiscoveryService {

    private final SearchProvider searchProvider;
    private final QueryBuilder queryBuilder;
    private final ResearchDiscoveryProperties discoveryProperties;

    @Override
    public List<DiscoveredSource> discoverSources(ResearchTarget target) {
        String query = queryBuilder.buildDiscoveryQuery(target);
        return searchWithProvider(query, discoveryProperties.maxResults());
    }

    @Override
    public List<DiscoveredSource> discoverAdaptiveSources(ResearchTarget target, List<String> missingFields, int maxResults) {
        String query = queryBuilder.buildAdaptiveQuery(target, missingFields);
        log.info("[Pipeline: ADAPTIVE_DISCOVERY] Searching provider '{}' for missing fields {} with query: '{}'",
                discoveryProperties.provider(), missingFields, query);
        return searchWithProvider(query, Math.max(1, maxResults));
    }

    private List<DiscoveredSource> searchWithProvider(String query, int maxResults) {
        log.info("[Pipeline: DISCOVERY] Searching provider '{}' with query: '{}'",
                discoveryProperties.provider(), query);
        try {
            return searchProvider.search(query, maxResults);
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (ExternalServiceException ex) {
            log.error("[Pipeline: FAILED] Search provider failure for query '{}': {}", query, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("[Pipeline: FAILED] Unexpected error during search discovery for query '{}': {}", query, ex.getMessage(), ex);
            throw new ExternalServiceException("Failed to retrieve research sources from provider", ex);
        }
    }
}
