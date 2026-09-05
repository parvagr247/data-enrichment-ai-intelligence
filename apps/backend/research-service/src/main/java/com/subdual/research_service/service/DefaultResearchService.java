package com.subdual.research_service.service;

import com.subdual.research_service.client.DatasetPersistenceClient;
import com.subdual.research_service.client.DefaultWebContentFetcher;
import com.subdual.research_service.client.FetchedContent;
import com.subdual.research_service.client.ResearchSourceClient;
import com.subdual.research_service.client.SearchDiscoveryProvider;
import com.subdual.research_service.client.WebContentFetcher;
import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.ResearchPipelineProperties;
import com.subdual.research_service.configuration.WebFetchProperties;
import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchStatus;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.EvidenceTuple;
import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import com.subdual.research_service.dto.ResearchResult;
import com.subdual.research_service.dto.SourceItem;
import com.subdual.research_service.exception.BusinessRuleException;
import com.subdual.research_service.exception.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production-oriented orchestration engine for research and entity enrichment.
 * Coordinates request validation, entity normalization, search discovery,
 * source normalization, classification, relevance evaluation, evidence extraction,
 * and response aggregation.
 */
@Service
public class DefaultResearchService implements ResearchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchService.class);

    private final SearchDiscoveryProvider searchDiscoveryProvider;
    private final ResearchDiscoveryProperties discoveryProperties;
    private final EntityNormalizer entityNormalizer;
    private final QueryBuilder queryBuilder;
    private final SourceProcessor sourceProcessor;
    private final WebContentFetcher webContentFetcher;
    private final ContentExtractor contentExtractor;
    private final EntityResolver entityResolver;
    private final EvidenceExtractor evidenceExtractor;
    private final ResearchPipelineProperties pipelineProperties;
    private final DatasetPersistenceClient datasetPersistenceClient;

    @Autowired
    public DefaultResearchService(
            SearchDiscoveryProvider searchDiscoveryProvider,
            ResearchDiscoveryProperties discoveryProperties,
            EntityNormalizer entityNormalizer,
            QueryBuilder queryBuilder,
            SourceProcessor sourceProcessor,
            WebContentFetcher webContentFetcher,
            ContentExtractor contentExtractor,
            EntityResolver entityResolver,
            EvidenceExtractor evidenceExtractor,
            ResearchPipelineProperties pipelineProperties,
            @Autowired(required = false) DatasetPersistenceClient datasetPersistenceClient
    ) {
        this.searchDiscoveryProvider = searchDiscoveryProvider;
        this.discoveryProperties = discoveryProperties;
        this.entityNormalizer = entityNormalizer;
        this.queryBuilder = queryBuilder;
        this.sourceProcessor = sourceProcessor;
        this.webContentFetcher = webContentFetcher;
        this.contentExtractor = contentExtractor;
        this.entityResolver = entityResolver;
        this.evidenceExtractor = evidenceExtractor;
        this.pipelineProperties = pipelineProperties;
        this.datasetPersistenceClient = datasetPersistenceClient;
    }

    public DefaultResearchService(
            SearchDiscoveryProvider searchDiscoveryProvider,
            ResearchDiscoveryProperties discoveryProperties,
            EntityNormalizer entityNormalizer,
            QueryBuilder queryBuilder,
            SourceProcessor sourceProcessor,
            WebContentFetcher webContentFetcher,
            ContentExtractor contentExtractor,
            EntityResolver entityResolver,
            EvidenceExtractor evidenceExtractor,
            ResearchPipelineProperties pipelineProperties
    ) {
        this(searchDiscoveryProvider, discoveryProperties, entityNormalizer, queryBuilder, sourceProcessor,
                webContentFetcher, contentExtractor, entityResolver, evidenceExtractor, pipelineProperties, null);
    }

    /**
     * Backward-compatible convenience constructor for unit tests and lightweight instantiation.
     */
    public DefaultResearchService(SearchDiscoveryProvider searchDiscoveryProvider, ResearchDiscoveryProperties discoveryProperties) {
        this(
                searchDiscoveryProvider,
                discoveryProperties,
                new DefaultEntityNormalizer(),
                new QueryBuilder(),
                new SourceProcessor(),
                new DefaultWebContentFetcher(
                        new WebFetchProperties(3000, 5000, 5, null),
                        discoveryProperties != null && "mock".equalsIgnoreCase(discoveryProperties.provider())
                ),
                new ContentExtractor(),
                new EntityResolver(),
                new EvidenceExtractor(),
                new ResearchPipelineProperties(discoveryProperties != null ? discoveryProperties.maxResults() : 5, 50000)
        );
    }

    @Override
    public ResearchResponse executeResearch(ResearchRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Request Validation
        validateRequest(request);

        // 2. Entity Normalization
        ResearchTarget target = entityNormalizer.normalize(request);
        log.info("[Pipeline: NORMALIZED] EntityId='{}', CanonicalUrl='{}', DisplayName='{}', Type='{}'",
                target.entityId(), target.canonicalUrl(), target.displayName(), target.entityType());

        // 3. Search Discovery
        String query = queryBuilder.buildDiscoveryQuery(target);
        log.info("[Pipeline: DISCOVERY] Searching provider '{}' with query: '{}'",
                discoveryProperties.provider(), query);
        List<DiscoveredSource> rawDiscoveredSources = discoverSources(query);

        // 4. Source Collection, Normalization, Classification, and Relevance Scoring
        log.info("[Pipeline: SOURCE_PROCESSING] Processing {} discovered candidate sources for entityId: '{}'",
                rawDiscoveredSources.size(), target.entityId());
        List<ResearchSource> rankedSources = sourceProcessor.processSources(
                rawDiscoveredSources,
                target,
                pipelineProperties.maxSources(),
                discoveryProperties.provider()
        );

        // 5. Evidence Extraction (Grounded attribute extraction from ranked sources)
        log.info("[Pipeline: EVIDENCE_EXTRACTION] Extracting evidence from {} ranked sources for entityId: '{}'",
                rankedSources.size(), target.entityId());
        Map<String, EvidenceTuple> attributes = extractEvidence(target, rankedSources);

        // 6. Response Aggregation
        long executionTimeMs = System.currentTimeMillis() - startTime;
        ResearchResponse response = aggregateResponse(target, rankedSources, attributes, rawDiscoveredSources.size(), executionTimeMs);

        // 7. Relational Persistence Boundary (non-blocking)
        if (datasetPersistenceClient != null) {
            try {
                datasetPersistenceClient.persistEntity(target, rankedSources, attributes);
            } catch (Exception ex) {
                log.warn("[Pipeline: PERSISTENCE_FAILED] Non-blocking dataset persistence failed: {}", ex.getMessage());
            }
        }

        log.info("[Pipeline: COMPLETED] Research finished for entityId: '{}' in {}ms (sources: {}, attributes: {})",
                target.entityId(), executionTimeMs, response.sources().size(), response.result().attributes().size());

        return response;
    }

    private void validateRequest(ResearchRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Request body must not be null");
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new BusinessRuleException("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
        }
    }

    private List<DiscoveredSource> discoverSources(String query) {
        try {
            if (searchDiscoveryProvider instanceof ResearchSourceClient client) {
                return client.discoverSources(query, discoveryProperties.maxResults());
            }
            return searchDiscoveryProvider.discover(query, discoveryProperties.maxResults());
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

    private Map<String, EvidenceTuple> extractEvidence(ResearchTarget target, List<ResearchSource> rankedSources) {
        List<ExtractedDocument> extractedDocuments = new ArrayList<>();
        Map<String, EntityResolver.ResolutionResult> resolutions = new HashMap<>();

        for (ResearchSource source : rankedSources) {
            FetchedContent fetched = webContentFetcher.fetch(source.url());
            if (fetched != null && fetched.success()) {
                ExtractedDocument doc = contentExtractor.extract(fetched, pipelineProperties.maxContentLength());
                extractedDocuments.add(doc);

                EntityResolver.ResolutionResult resolution = entityResolver.resolve(target, doc);
                resolutions.put(source.url(), resolution);
            } else {
                log.debug("Skipping unretrievable source URL '{}'", source.url());
            }
        }

        return evidenceExtractor.extractEvidence(
                target,
                rankedSources,
                extractedDocuments,
                resolutions
        );
    }

    private ResearchResponse aggregateResponse(
            ResearchTarget target,
            List<ResearchSource> rankedSources,
            Map<String, EvidenceTuple> attributes,
            int totalDiscovered,
            long executionTimeMs
    ) {
        List<SourceItem> sourceItems = rankedSources.stream()
                .map(this::toSourceItem)
                .toList();

        ResearchResult result = new ResearchResult(
                target.displayName(),
                target.entityType(),
                target.canonicalUrl(),
                attributes
        );

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", discoveryProperties.provider());
        metadata.put("totalSourcesDiscovered", totalDiscovered);
        metadata.put("totalSourcesRanked", rankedSources.size());
        metadata.put("attributesExtracted", attributes.size());

        return new ResearchResponse(
                ResearchStatus.COMPLETED,
                target.entityId(),
                result,
                sourceItems,
                executionTimeMs,
                metadata
        );
    }

    private SourceItem toSourceItem(ResearchSource source) {
        return new SourceItem(
                source.url(),
                source.title(),
                source.snippet(),
                source.sourceType(),
                source.domain(),
                source.provider(),
                source.retrievedAt(),
                source.relevance()
        );
    }
}
