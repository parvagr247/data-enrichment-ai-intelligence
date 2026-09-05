package com.subdual.research_service.service;

import com.subdual.research_service.client.DefaultWebContentFetcher;
import com.subdual.research_service.client.FetchedContent;
import com.subdual.research_service.client.ResearchSourceClient;
import com.subdual.research_service.client.SearchProvider;
import com.subdual.research_service.client.WebContentFetcher;
import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.ResearchPipelineProperties;
import com.subdual.research_service.configuration.WebFetchProperties;
import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.EntityType;
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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DefaultResearchService implements ResearchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchService.class);

    private final SearchProvider searchProvider;
    private final ResearchDiscoveryProperties discoveryProperties;
    private final QueryBuilder queryBuilder;
    private final SourceProcessor sourceProcessor;
    private final WebContentFetcher webContentFetcher;
    private final ContentExtractor contentExtractor;
    private final EntityResolver entityResolver;
    private final EvidenceExtractor evidenceExtractor;
    private final ResearchPipelineProperties pipelineProperties;

    @Autowired
    public DefaultResearchService(
            SearchProvider searchProvider,
            ResearchDiscoveryProperties discoveryProperties,
            QueryBuilder queryBuilder,
            SourceProcessor sourceProcessor,
            WebContentFetcher webContentFetcher,
            ContentExtractor contentExtractor,
            EntityResolver entityResolver,
            EvidenceExtractor evidenceExtractor,
            ResearchPipelineProperties pipelineProperties
    ) {
        this.searchProvider = searchProvider;
        this.discoveryProperties = discoveryProperties;
        this.queryBuilder = queryBuilder;
        this.sourceProcessor = sourceProcessor;
        this.webContentFetcher = webContentFetcher;
        this.contentExtractor = contentExtractor;
        this.entityResolver = entityResolver;
        this.evidenceExtractor = evidenceExtractor;
        this.pipelineProperties = pipelineProperties;
    }

    public DefaultResearchService(SearchProvider searchProvider, ResearchDiscoveryProperties discoveryProperties) {
        this(
                searchProvider,
                discoveryProperties,
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
        return research(request);
    }

    @Override
    public ResearchResponse research(ResearchRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Stage: RECEIVED & Validation
        log.info("[Pipeline: RECEIVED] Research request received for URL: '{}', entityType: '{}', name: '{}'",
                request != null ? request.url() : null,
                request != null ? request.entityType() : null,
                request != null ? request.name() : null);

        validateRequest(request);
        ResearchTarget target = buildTarget(request);

        // 2. Stage: SEARCHING (Query construction & Discovery)
        String query = queryBuilder.buildDiscoveryQuery(target);
        log.info("[Pipeline: SEARCHING] Discovery started for entityId: '{}', query: '{}'", target.entityId(), query);

        List<DiscoveredSource> rawDiscoveredSources;
        try {
            if (searchProvider instanceof ResearchSourceClient client) {
                rawDiscoveredSources = client.discoverSources(query, discoveryProperties.maxResults());
            } else {
                rawDiscoveredSources = searchProvider.search(query, discoveryProperties.maxResults());
            }
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (ExternalServiceException ex) {
            log.error("[Pipeline: FAILED] Search provider failure for query '{}': {}", query, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("[Pipeline: FAILED] Unexpected failure during search discovery for query '{}'", query, ex);
            throw new ExternalServiceException("Failed to retrieve research sources", ex);
        }

        // 3. Stage: COLLECTING_SOURCES (Normalization, Deduplication, Quality Ranking)
        log.info("[Pipeline: COLLECTING_SOURCES] Processing and ranking discovered sources for entityId: '{}'", target.entityId());
        List<ResearchSource> rankedSources = sourceProcessor.processSources(
                rawDiscoveredSources,
                target,
                pipelineProperties.maxSources()
        );

        // 4. Stage: EXTRACTING_EVIDENCE & Web Content Retrieval
        log.info("[Pipeline: EXTRACTING_EVIDENCE] Retrieving content from {} sources for entityId: '{}'",
                rankedSources.size(), target.entityId());

        List<ExtractedDocument> extractedDocuments = new ArrayList<>();
        Map<String, EntityResolver.ResolutionResult> resolutions = new HashMap<>();

        for (ResearchSource source : rankedSources) {
            FetchedContent fetched = webContentFetcher.fetch(source.url());
            if (fetched.success()) {
                ExtractedDocument doc = contentExtractor.extract(fetched, pipelineProperties.maxContentLength());
                extractedDocuments.add(doc);

                EntityResolver.ResolutionResult resolution = entityResolver.resolve(target, doc);
                resolutions.put(source.url(), resolution);
            } else {
                log.debug("Skipping failed content fetch for source URL '{}': {}", source.url(), fetched.errorMessage());
            }
        }

        // 5. Stage: ENRICHING (Attribute extraction, confidence calculation, provenance mapping)
        log.info("[Pipeline: ENRICHING] Extracting attributes and binding provenance for entityId: '{}'", target.entityId());
        Map<String, EvidenceTuple> attributes = evidenceExtractor.extractEvidence(
                target,
                rankedSources,
                extractedDocuments,
                resolutions
        );

        // 6. Stage: COMPLETED (Build final response)
        long executionTimeMs = System.currentTimeMillis() - startTime;
        ResearchStatus finalStatus = ResearchStatus.COMPLETED;

        log.info("[Pipeline: COMPLETED] Research completed for entityId: '{}' in {}ms with {} sources, {} attributes",
                target.entityId(), executionTimeMs, rankedSources.size(), attributes.size());

        List<SourceItem> sourceItems = rankedSources.stream()
                .map(this::mapSource)
                .toList();

        ResearchResult result = new ResearchResult(
                target.displayName(),
                target.entityType(),
                target.canonicalUrl(),
                attributes
        );

        return new ResearchResponse(
                finalStatus,
                target.entityId(),
                result,
                sourceItems,
                executionTimeMs
        );
    }

    private void validateRequest(ResearchRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Request body must not be null");
        }
        if (request.url() == null || request.url().isBlank()) {
            throw new BusinessRuleException("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
        }
    }

    private ResearchTarget buildTarget(ResearchRequest request) {
        String canonicalUrl = canonicalizeUrl(request.url());
        String entityId = computeEntityId(canonicalUrl);
        EntityType type = request.entityType() != null ? request.entityType() : EntityType.OTHER;
        String displayName = (request.name() != null && !request.name().isBlank())
                ? request.name().trim()
                : canonicalUrl;

        return new ResearchTarget(request.url(), canonicalUrl, entityId, type, displayName);
    }

    private SourceItem mapSource(ResearchSource source) {
        return new SourceItem(
                source.url(),
                source.title(),
                source.sourceType(),
                source.retrievedAt(),
                source.relevance()
        );
    }

    private String canonicalizeUrl(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            int port = uri.getPort();
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";

            String portPart = (port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
                    ? "" : ":" + port;

            return scheme + "://" + host + portPart + path + query;
        } catch (Exception e) {
            return rawUrl.trim();
        }
    }

    private String computeEntityId(String canonicalUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalUrl.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
