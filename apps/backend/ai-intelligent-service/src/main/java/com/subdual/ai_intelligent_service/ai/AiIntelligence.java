package com.subdual.ai_intelligent_service.ai;

import com.subdual.ai_intelligent_service.ai.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionResponse;

/**
 * Clean AI model/provider abstraction.
 */
public interface AiIntelligence {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    ExtractionResponse extractFacts(ExtractionRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AiExecutionMetrics getLatestMetrics();
}
