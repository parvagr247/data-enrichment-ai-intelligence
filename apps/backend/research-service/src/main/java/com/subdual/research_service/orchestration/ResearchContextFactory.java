package com.subdual.research_service.orchestration;

import com.subdual.research_service.diagnostics.ResearchDiagnostics;
import com.subdual.research_service.dto.request.ResearchRequest;
import org.springframework.stereotype.Component;

/**
 * Factory for creating initialized ResearchContext instances per request.
 */
@Component
public class ResearchContextFactory {

    public ResearchContext create(ResearchRequest request) {
        return new ResearchContext(
                request,
                ResearchExecutionTimer.start(),
                new ResearchDiagnostics()
        );
    }
}
