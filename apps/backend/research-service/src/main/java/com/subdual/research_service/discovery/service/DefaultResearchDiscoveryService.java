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
        long startTime = System.currentTimeMillis();
        String query = queryBuilder.buildDiscoveryQuery(target);
        List<DiscoveredSource> raw = searchWithProvider(query, discoveryProperties.maxResults());

        var dedupResult = com.subdual.research_service.discovery.ranking.SourceDeduplicator.deduplicate(raw);
        List<DiscoveredSource> ranked = com.subdual.research_service.discovery.ranking.SourceRanker.rankSources(
                dedupResult.deduplicated(), target, com.subdual.research_service.discovery.model.QueryIntent.GENERAL_PROFILE
        );

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Pipeline: DISCOVERY_METRICS] Provider: '{}', Queries: 1, Results: {}, DedupRemoved: {}, Selected: {}, Duration: {}ms",
                searchProvider.providerName(), raw.size(), dedupResult.duplicatesRemovedCount(), ranked.size(), duration);

        return ranked;
    }

    @Override
    public List<DiscoveredSource> discoverAdaptiveSources(ResearchTarget target, List<String> missingFields, int maxResults) {
        long startTime = System.currentTimeMillis();
        String query = queryBuilder.buildAdaptiveQuery(target, missingFields);
        log.info("[Pipeline: ADAPTIVE_DISCOVERY] Searching provider '{}' for missing fields {} with query: '{}'",
                searchProvider.providerName(), missingFields, query);

        List<DiscoveredSource> raw = searchWithProvider(query, Math.max(1, maxResults));
        var dedupResult = com.subdual.research_service.discovery.ranking.SourceDeduplicator.deduplicate(raw);
        List<DiscoveredSource> ranked = com.subdual.research_service.discovery.ranking.SourceRanker.rankSources(
                dedupResult.deduplicated(), target, com.subdual.research_service.discovery.QueryBuilder.mapFieldToIntent(
                        missingFields != null && !missingFields.isEmpty() ? missingFields.get(0) : null
                )
        );

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Pipeline: ADAPTIVE_METRICS] Provider: '{}', MissingFields: {}, Results: {}, DedupRemoved: {}, Selected: {}, Duration: {}ms",
                searchProvider.providerName(), missingFields, raw.size(), dedupResult.duplicatesRemovedCount(), ranked.size(), duration);

        return ranked;
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
