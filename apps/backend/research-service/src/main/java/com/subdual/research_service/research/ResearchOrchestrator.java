package com.subdual.research_service.research;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;
import com.subdual.research_service.common.validation.ResearchRequestValidator;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.discovery.ResearchDiscoveryService;
import com.subdual.research_service.extraction.SourceEvidenceService;
import com.subdual.research_service.integration.persistence.ResearchSnapshotPersister;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.EntityNormalizer;
import com.subdual.research_service.research.pipeline.ResearchContext;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import com.subdual.research_service.research.pipeline.ResearchExecutionTimer;
import com.subdual.research_service.research.pipeline.ResearchResponseFactory;
import com.subdual.research_service.research.pipeline.SourceProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Clean, intention-revealing pipeline orchestrator coordinating the research lifecycle.
 * Executes steps: validate -> normalize -> discover -> process sources -> extract evidence -> persist -> assemble response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResearchOrchestrator implements ResearchService {

    private final ResearchRequestValidator validator;
    private final EntityNormalizer entityNormalizer;
    private final ResearchDiscoveryService discoveryService;
    private final SourceProcessor sourceProcessor;
    private final SourceEvidenceService sourceEvidenceService;
    private final ResearchSnapshotPersister persister;
    private final ResearchResponseFactory responseFactory;
    private final ResearchDiscoveryProperties discoveryProperties;
    private final ResearchPipelineProperties pipelineProperties;

    @Override
    public ResearchResponse executeResearch(ResearchRequest request) {
        ResearchExecutionTimer timer = ResearchExecutionTimer.start();
        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        validate(request);
        ResearchTarget target = normalize(request);

        MDC.put("entityId", target.entityId());
        try {
            List<DiscoveredSource> rawSources = discover(target);
            List<ResearchSource> rankedSources = processSources(rawSources, target);
            Map<String, EvidenceTuple> attributes = extractEvidence(target, rankedSources, diagnostics);
            persistSnapshot(target, rankedSources, attributes, diagnostics);

            return assembleResponse(target, rankedSources, attributes, rawSources.size(), timer.elapsedMillis(), diagnostics);
        } finally {
            MDC.remove("entityId");
        }
    }

    public ResearchResponse execute(ResearchContext context) {
        validate(context.request());
        ResearchTarget target = normalize(context.request());
        context.setTarget(target);

        MDC.put("entityId", target.entityId());
        try {
            List<DiscoveredSource> rawSources = discover(target);
            context.setRawDiscoveredSources(rawSources);

            List<ResearchSource> rankedSources = processSources(rawSources, target);
            context.setRankedSources(rankedSources);

            Map<String, EvidenceTuple> attributes = extractEvidence(target, rankedSources, context.diagnostics());
            context.setAttributes(attributes);

            persistSnapshot(target, rankedSources, attributes, context.diagnostics());

            return assembleResponse(target, rankedSources, attributes, rawSources.size(), context.timer().elapsedMillis(), context.diagnostics());
        } finally {
            MDC.remove("entityId");
        }
    }

    public void validate(ResearchRequest request) {
        validator.validate(request);
    }

    public ResearchTarget normalize(ResearchRequest request) {
        ResearchTarget target = entityNormalizer.normalize(request);
        log.info("[Pipeline: NORMALIZED] EntityId='{}', CanonicalUrl='{}', DisplayName='{}', Type='{}'",
                target.entityId(), target.canonicalUrl(), target.displayName(), target.entityType());
        return target;
    }

    public List<DiscoveredSource> discover(ResearchTarget target) {
        return discoveryService.discoverSources(target);
    }

    public List<ResearchSource> processSources(List<DiscoveredSource> rawSources, ResearchTarget target) {
        int maxSources = pipelineProperties != null ? pipelineProperties.maxSources() : 5;
        String provider = discoveryProperties != null ? discoveryProperties.provider() : "mock";
        return sourceProcessor.processSources(rawSources, target, maxSources, provider);
    }

    public Map<String, EvidenceTuple> extractEvidence(ResearchTarget target, List<ResearchSource> rankedSources, ResearchDiagnostics diagnostics) {
        return sourceEvidenceService.extractEvidence(target, rankedSources, diagnostics);
    }

    public void persistSnapshot(ResearchTarget target, List<ResearchSource> rankedSources, Map<String, EvidenceTuple> attributes, ResearchDiagnostics diagnostics) {
        persister.persistSnapshot(target, rankedSources, attributes, diagnostics);
    }

    public ResearchResponse assembleResponse(ResearchTarget target, List<ResearchSource> rankedSources, Map<String, EvidenceTuple> attributes, int totalDiscovered, long executionTimeMs, ResearchDiagnostics diagnostics) {
        String provider = discoveryProperties != null ? discoveryProperties.provider() : "mock";
        ResearchResponse response = responseFactory.createResponse(
                target, rankedSources, attributes, totalDiscovered, executionTimeMs, diagnostics, provider
        );
        log.info("[Pipeline: COMPLETED] Research finished for entityId: '{}' in {}ms (sources: {}, attributes: {}, warnings: {})",
                target.entityId(), executionTimeMs, response.sources().size(),
                response.result().attributes().size(), diagnostics.warningCount());
        return response;
    }
}
