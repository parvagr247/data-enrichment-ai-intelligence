package com.subdual.research_service.service;

import com.subdual.research_service.source.DefaultWebContentFetcher;
import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.ResearchPipelineProperties;
import com.subdual.research_service.configuration.WebFetchProperties;
import com.subdual.research_service.discovery.DefaultResearchDiscoveryService;
import com.subdual.research_service.discovery.QueryBuilder;
import com.subdual.research_service.discovery.ResearchDiscoveryService;
import com.subdual.research_service.discovery.SearchProvider;
import com.subdual.research_service.dto.request.ResearchRequest;
import com.subdual.research_service.dto.response.ResearchResponse;
import com.subdual.research_service.source.ContentExtractor;
import com.subdual.research_service.extraction.DefaultSourceEvidenceService;
import com.subdual.research_service.extraction.EntityResolver;
import com.subdual.research_service.extraction.EvidenceExtractor;
import com.subdual.research_service.extraction.SourceEvidenceService;
import com.subdual.research_service.normalization.DefaultEntityNormalizer;
import com.subdual.research_service.normalization.EntityNormalizer;
import com.subdual.research_service.orchestration.DefaultResearchPipeline;
import com.subdual.research_service.orchestration.ResearchContext;
import com.subdual.research_service.orchestration.ResearchContextFactory;
import com.subdual.research_service.orchestration.ResearchPipeline;
import com.subdual.research_service.persistence.DefaultResearchSnapshotPersister;
import com.subdual.research_service.persistence.ResearchSnapshotPersister;
import com.subdual.research_service.source.SourceProcessor;
import com.subdual.research_service.response.ResearchResponseFactory;
import com.subdual.research_service.validation.ResearchRequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Application use-case entry point for entity research and enrichment.
 * Acts as a thin facade delegating to ResearchPipeline.
 */
@Service
public class DefaultResearchService implements ResearchService {

    private final ResearchContextFactory contextFactory;
    private final ResearchPipeline researchPipeline;

    @Autowired
    public DefaultResearchService(ResearchContextFactory contextFactory, ResearchPipeline researchPipeline) {
        this.contextFactory = contextFactory;
        this.researchPipeline = researchPipeline;
    }

    /**
     * Backward-compatible convenience constructor for tests and programmatic usage.
     */
    public DefaultResearchService(SearchProvider searchProvider, ResearchDiscoveryProperties discoveryProperties) {
        ResearchDiscoveryProperties discProps = discoveryProperties != null
                ? discoveryProperties
                : new ResearchDiscoveryProperties("mock", "", "https://api.tavily.com", 5, 4000);
        ResearchPipelineProperties pipeProps = new ResearchPipelineProperties(discProps.maxResults(), 50000);
        WebFetchProperties webProps = new WebFetchProperties(3000, 5000, 5, null);
        boolean isMock = "mock".equalsIgnoreCase(discProps.provider());

        ResearchRequestValidator validator = new ResearchRequestValidator();
        EntityNormalizer normalizer = new DefaultEntityNormalizer();
        ResearchDiscoveryService discoveryService = new DefaultResearchDiscoveryService(searchProvider, new QueryBuilder(), discProps);
        SourceProcessor sourceProcessor = new SourceProcessor();
        SourceEvidenceService evidenceService = new DefaultSourceEvidenceService(
                new DefaultWebContentFetcher(webProps, isMock),
                new ContentExtractor(),
                new EntityResolver(),
                new EvidenceExtractor(),
                pipeProps
        );
        ResearchSnapshotPersister persister = new DefaultResearchSnapshotPersister();
        ResearchResponseFactory responseFactory = new ResearchResponseFactory();

        this.contextFactory = new ResearchContextFactory();
        this.researchPipeline = new DefaultResearchPipeline(
                validator,
                normalizer,
                discoveryService,
                sourceProcessor,
                evidenceService,
                persister,
                responseFactory,
                discProps,
                pipeProps
        );
    }

    @Override
    public ResearchResponse executeResearch(ResearchRequest request) {
        ResearchContext context = contextFactory.create(request);
        return researchPipeline.execute(context);
    }
}
