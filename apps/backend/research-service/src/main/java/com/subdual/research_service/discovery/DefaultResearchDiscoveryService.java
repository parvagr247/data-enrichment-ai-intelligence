package com.subdual.research_service.discovery;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.exception.BusinessRuleException;
import com.subdual.research_service.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default implementation of ResearchDiscoveryService isolating query generation,
 * search provider invocation, and error handling.
 */
@Service
public class DefaultResearchDiscoveryService implements ResearchDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchDiscoveryService.class);

    private final SearchProvider searchProvider;
    private final QueryBuilder queryBuilder;
    private final ResearchDiscoveryProperties discoveryProperties;

    public DefaultResearchDiscoveryService(
            SearchProvider searchProvider,
            QueryBuilder queryBuilder,
            ResearchDiscoveryProperties discoveryProperties
    ) {
        this.searchProvider = searchProvider;
        this.queryBuilder = queryBuilder;
        this.discoveryProperties = discoveryProperties;
    }

    @Override
    public List<DiscoveredSource> discoverSources(ResearchTarget target) {
        String query = queryBuilder.buildDiscoveryQuery(target);
        log.info("[Pipeline: DISCOVERY] Searching provider '{}' with query: '{}'",
                discoveryProperties.provider(), query);

        try {
            return searchProvider.discoverSources(query, discoveryProperties.maxResults());
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
