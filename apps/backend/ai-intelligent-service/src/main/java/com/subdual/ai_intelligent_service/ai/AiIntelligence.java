package com.subdual.ai_intelligent_service.ai;

import com.subdual.ai_intelligent_service.ai.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.response.ExtractionResponse;

/**
 * Clean AI model/provider abstraction.
 */
public interface AiIntelligence {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    ExtractionResponse extractFacts(ExtractionRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AiExecutionMetrics getLatestMetrics();
}
