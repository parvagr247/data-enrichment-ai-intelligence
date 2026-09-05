package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Execution-scoped state container representing the lifecycle of one research operation.
 * Eliminates parameter explosion across pipeline stages while keeping state strictly bounded.
 */
public class ResearchContext {

    private final ResearchRequest request;
    private final ResearchExecutionTimer timer;
    private final ResearchDiagnostics diagnostics;

    private ResearchTarget target;
    private List<DiscoveredSource> rawDiscoveredSources = Collections.emptyList();
    private List<ResearchSource> rankedSources = Collections.emptyList();
    private Map<String, EvidenceTuple> attributes = Collections.emptyMap();

    public ResearchContext(ResearchRequest request, ResearchExecutionTimer timer, ResearchDiagnostics diagnostics) {
        this.request = request;
        this.timer = timer;
        this.diagnostics = diagnostics;
    }

    public ResearchContext(ResearchRequest request) {
        this(request, ResearchExecutionTimer.start(), new ResearchDiagnostics());
    }

    public ResearchRequest request() {
        return request;
    }

    public ResearchExecutionTimer timer() {
        return timer;
    }

    public ResearchDiagnostics diagnostics() {
        return diagnostics;
    }

    public ResearchTarget target() {
        return target;
    }

    public void setTarget(ResearchTarget target) {
        this.target = target;
    }

    public List<DiscoveredSource> rawDiscoveredSources() {
        return rawDiscoveredSources;
    }

    public void setRawDiscoveredSources(List<DiscoveredSource> rawDiscoveredSources) {
        this.rawDiscoveredSources = rawDiscoveredSources != null ? rawDiscoveredSources : Collections.emptyList();
    }

    public List<ResearchSource> rankedSources() {
        return rankedSources;
    }

    public void setRankedSources(List<ResearchSource> rankedSources) {
        this.rankedSources = rankedSources != null ? rankedSources : Collections.emptyList();
    }

    public Map<String, EvidenceTuple> attributes() {
        return attributes;
    }

    public void setAttributes(Map<String, EvidenceTuple> attributes) {
        this.attributes = attributes != null ? attributes : Collections.emptyMap();
    }

    public int totalDiscoveredCount() {
        return rawDiscoveredSources.size();
    }

    public int rankedCount() {
        return rankedSources.size();
    }

    public int attributeCount() {
        return attributes.size();
    }
}
