package com.subdual.research_service.orchestration;

import com.subdual.research_service.configuration.ResearchDiscoveryProperties;
import com.subdual.research_service.configuration.ResearchPipelineProperties;
import com.subdual.research_service.discovery.ResearchDiscoveryService;
import com.subdual.research_service.domain.DiscoveredSource;
import com.subdual.research_service.domain.ResearchSource;
import com.subdual.research_service.domain.ResearchTarget;
import com.subdual.research_service.dto.response.EvidenceTuple;
import com.subdual.research_service.dto.response.ResearchResponse;
import com.subdual.research_service.extraction.SourceEvidenceService;
import com.subdual.research_service.normalization.EntityNormalizer;
import com.subdual.research_service.persistence.ResearchSnapshotPersister;
import com.subdual.research_service.source.SourceProcessor;
import com.subdual.research_service.response.ResearchResponseFactory;
import com.subdual.research_service.validation.ResearchRequestValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Standard implementation of ResearchPipeline coordinating validation, normalization,
 * discovery, source processing, grounded evidence extraction, persistence, and response creation.
 */
@Component
public class DefaultResearchPipeline implements ResearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(DefaultResearchPipeline.class);

    private final ResearchRequestValidator validator;
    private final EntityNormalizer entityNormalizer;
    private final ResearchDiscoveryService discoveryService;
    private final SourceProcessor sourceProcessor;
    private final SourceEvidenceService sourceEvidenceService;
    private final ResearchSnapshotPersister persister;
    private final ResearchResponseFactory responseFactory;
    private final ResearchDiscoveryProperties discoveryProperties;
    private final ResearchPipelineProperties pipelineProperties;

    @Autowired
    public DefaultResearchPipeline(
            ResearchRequestValidator validator,
            EntityNormalizer entityNormalizer,
            ResearchDiscoveryService discoveryService,
            SourceProcessor sourceProcessor,
            SourceEvidenceService sourceEvidenceService,
            ResearchSnapshotPersister persister,
            ResearchResponseFactory responseFactory,
            ResearchDiscoveryProperties discoveryProperties,
            ResearchPipelineProperties pipelineProperties
    ) {
        this.validator = validator;
        this.entityNormalizer = entityNormalizer;
        this.discoveryService = discoveryService;
        this.sourceProcessor = sourceProcessor;
        this.sourceEvidenceService = sourceEvidenceService;
        this.persister = persister;
        this.responseFactory = responseFactory;
        this.discoveryProperties = discoveryProperties;
        this.pipelineProperties = pipelineProperties;
    }

    @Override
    public ResearchResponse execute(ResearchContext context) {
        // 1. Request Validation
        validator.validate(context.request());

        // 2. Entity Normalization
        ResearchTarget target = entityNormalizer.normalize(context.request());
        context.setTarget(target);
        MDC.put("entityId", target.entityId());

        try {
            log.info("[Pipeline: NORMALIZED] EntityId='{}', CanonicalUrl='{}', DisplayName='{}', Type='{}'",
                    target.entityId(), target.canonicalUrl(), target.displayName(), target.entityType());

            // 3. Search Discovery
            List<DiscoveredSource> rawDiscoveredSources = discoveryService.discoverSources(target);
            context.setRawDiscoveredSources(rawDiscoveredSources);

            // 4. Source Processing, Deduplication, and Relevance Ranking
            int maxSources = pipelineProperties != null ? pipelineProperties.maxSources() : 5;
            String provider = discoveryProperties != null ? discoveryProperties.provider() : "mock";
            List<ResearchSource> rankedSources = sourceProcessor.processSources(rawDiscoveredSources, target, maxSources, provider);
            context.setRankedSources(rankedSources);

            // 5. Grounded Evidence Extraction
            Map<String, EvidenceTuple> attributes = sourceEvidenceService.extractEvidence(target, rankedSources, context.diagnostics());
            context.setAttributes(attributes);

            // 6. Non-blocking Relational Persistence
            persister.persistSnapshot(target, rankedSources, attributes, context.diagnostics());

            // 7. Response Construction
            long executionTimeMs = context.timer().elapsedMillis();
            ResearchResponse response = responseFactory.createResponse(
                    target,
                    rankedSources,
                    attributes,
                    rawDiscoveredSources.size(),
                    executionTimeMs,
                    context.diagnostics(),
                    provider
            );

            log.info("[Pipeline: COMPLETED] Research finished for entityId: '{}' in {}ms (sources: {}, attributes: {}, warnings: {})",
                    target.entityId(), executionTimeMs, response.sources().size(), response.result().attributes().size(), context.diagnostics().warningCount());

            return response;
        } finally {
            MDC.remove("entityId");
        }
    }
}
