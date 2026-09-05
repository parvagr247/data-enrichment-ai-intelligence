package com.subdual.research_service.research;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;
import com.subdual.research_service.common.validation.ResearchRequestValidator;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.discovery.service.ResearchDiscoveryService;
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

import com.subdual.research_service.research.model.ConfidenceTier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        return execute(new ResearchContext(request));
    }

    public ResearchResponse execute(ResearchContext context) {
        validate(context.request());
        ResearchTarget target = normalize(context.request());
        context.setTarget(target);

        MDC.put("entityId", target.entityId());
        try {
            List<DiscoveredSource> rawSources = discover(target);
            context.setRawDiscoveredSources(rawSources);

            List<ResearchSource> rankedSources = new ArrayList<>(processSources(rawSources, target));
            context.setRankedSources(rankedSources);

            Map<String, EvidenceTuple> attributes = new LinkedHashMap<>(extractEvidence(target, rankedSources, context.diagnostics()));

            executeAdaptiveResearchIfRequired(target, rankedSources, attributes, context.diagnostics());

            sourceEvidenceService.applyTargetFields(target, attributes);
            context.setAttributes(attributes);

            persistSnapshot(target, rankedSources, attributes, context.diagnostics());

            return assembleResponse(target, rankedSources, attributes, rawSources.size(), context.timer().elapsedMillis(), context.diagnostics());
        } finally {
            MDC.remove("entityId");
        }
    }

    private void executeAdaptiveResearchIfRequired(
            ResearchTarget target,
            List<ResearchSource> currentSources,
            Map<String, EvidenceTuple> attributes,
            ResearchDiagnostics diagnostics
    ) {
        if (target == null || target.targetFields() == null || target.targetFields().isEmpty()) {
            return;
        }

        int maxAdaptive = target.depth() != null ? target.depth().maxAdaptiveQueries() : 1;
        if (maxAdaptive <= 0) {
            log.info("[Pipeline: ADAPTIVE_STOP] Depth '{}' allows 0 adaptive queries. Skipping follow-up search.", target.depth());
            return;
        }

        int adaptiveQueriesRun = 0;
        while (adaptiveQueriesRun < maxAdaptive) {
            List<String> missingFields = identifyMissingTargetFields(target, attributes);
            if (missingFields.isEmpty()) {
                log.info("[Pipeline: ADAPTIVE_STOP] All requested target fields covered. Stopping early.");
                break;
            }

            int currentCount = currentSources.size();
            int maxTotal = target.depth() != null ? target.depth().maxSources() : 5;
            int remainingAllowed = maxTotal - currentCount;
            if (remainingAllowed <= 0) {
                log.info("[Pipeline: ADAPTIVE_STOP] Source limit reached ({}). Stopping.", currentCount);
                break;
            }

            List<DiscoveredSource> newDiscovered = discoveryService.discoverAdaptiveSources(target, missingFields, Math.min(3, remainingAllowed));
            adaptiveQueriesRun++;

            if (newDiscovered == null || newDiscovered.isEmpty()) {
                log.info("[Pipeline: ADAPTIVE_STOP] No new sources discovered for missing fields {}. Stopping.", missingFields);
                break;
            }

            List<ResearchSource> newRanked = filterUnseenSources(newDiscovered, currentSources, target);
            if (newRanked.isEmpty()) {
                log.info("[Pipeline: ADAPTIVE_STOP] Discovered candidates already seen or filtered. Stopping.");
                break;
            }

            currentSources.addAll(newRanked);
            Map<String, EvidenceTuple> additionalAttrs = extractEvidence(target, newRanked, diagnostics);
            mergeAdditionalAttributes(attributes, additionalAttrs);
        }
    }

    private List<String> identifyMissingTargetFields(ResearchTarget target, Map<String, EvidenceTuple> attributes) {
        return target.targetFields().stream()
                .filter(field -> !isFieldSatisfied(attributes, field))
                .toList();
    }

    private boolean isFieldSatisfied(Map<String, EvidenceTuple> attributes, String field) {
        if (attributes == null || field == null) return false;
        EvidenceTuple tuple = attributes.get(field);
        if (tuple == null) {
            for (Map.Entry<String, EvidenceTuple> entry : attributes.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(field)) {
                    tuple = entry.getValue();
                    break;
                }
            }
        }
        return tuple != null && tuple.value() != null
                && !"UNKNOWN".equalsIgnoreCase(tuple.value().trim())
                && tuple.confidence() != ConfidenceTier.UNKNOWN;
    }

    private List<ResearchSource> filterUnseenSources(
            List<DiscoveredSource> candidates,
            List<ResearchSource> existingSources,
            ResearchTarget target
    ) {
        Set<String> existingUrls = existingSources.stream()
                .map(ResearchSource::url)
                .filter(u -> u != null && !u.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<DiscoveredSource> fresh = candidates.stream()
                .filter(c -> c != null && c.url() != null && !existingUrls.contains(c.url().toLowerCase()))
                .toList();

        if (fresh.isEmpty()) {
            return List.of();
        }

        int maxRemaining = Math.max(1, (target.depth() != null ? target.depth().maxSources() : 5) - existingSources.size());
        String provider = discoveryProperties != null ? discoveryProperties.provider() : "mock";
        return sourceProcessor.processSources(fresh, target, maxRemaining, provider);
    }

    private void mergeAdditionalAttributes(Map<String, EvidenceTuple> target, Map<String, EvidenceTuple> incoming) {
        if (incoming == null || target == null) return;
        incoming.forEach((key, val) -> {
            if (val != null && val.value() != null && !"UNKNOWN".equalsIgnoreCase(val.value().trim())) {
                target.put(key, val);
            }
        });
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
