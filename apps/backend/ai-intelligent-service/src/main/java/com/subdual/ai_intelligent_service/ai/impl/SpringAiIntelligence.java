package com.subdual.ai_intelligent_service.ai.impl;

import com.subdual.ai_intelligent_service.ai.AiIntelligence;
import com.subdual.ai_intelligent_service.ai.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.enrichment.service.EnrichmentAIService;
import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.response.ExtractionResponse;
import com.subdual.ai_intelligent_service.extraction.service.ExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Clean delegation implementation of AiIntelligence unifying enrichment and extraction services.
 */
@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class SpringAiIntelligence implements AiIntelligence {

    private final EnrichmentAIService enrichmentAIService;
    private final ExtractionService extractionService;

    private volatile AiExecutionMetrics latestMetrics = AiExecutionMetrics.deterministic(0L);

    @Override
    public RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request) {
        long start = System.currentTimeMillis();
        RequirementInterpretationResponse response = enrichmentAIService.interpretRequirement(request);
        latestMetrics = new AiExecutionMetrics("gemini-model", 0, 0, 0, System.currentTimeMillis() - start, 1, 0, "SUCCESS");
        return response;
    }

    @Override
    public ExtractionResponse extractFacts(ExtractionRequest request) {
        long start = System.currentTimeMillis();
        ExtractionResponse response = extractionService.extractFacts(request);
        latestMetrics = new AiExecutionMetrics("gemini-model", 0, 0, 0, System.currentTimeMillis() - start, 1, 0, "SUCCESS");
        return response;
    }

    @Override
    public InputCleansingResponse cleanInput(InputCleansingRequest request) {
        return enrichmentAIService.cleanInput(request);
    }

    @Override
    public AiExecutionMetrics getLatestMetrics() {
        return latestMetrics;
    }
}
