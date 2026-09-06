package com.subdual.ai_intelligent_service.service.ai;

import com.subdual.ai_intelligent_service.dto.AiExecutionMetrics;
import com.subdual.ai_intelligent_service.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.dto.ExtractionResponse;
import com.subdual.ai_intelligent_service.dto.InputCleansingRequest;
import com.subdual.ai_intelligent_service.dto.InputCleansingResponse;
import com.subdual.ai_intelligent_service.dto.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.dto.RequirementInterpretationResponse;

/**
 * Clean AI model/provider abstraction (Task 72).
 */
public interface AiIntelligence {

    RequirementInterpretationResponse interpretRequirement(RequirementInterpretationRequest request);

    ExtractionResponse extractFacts(ExtractionRequest request);

    InputCleansingResponse cleanInput(InputCleansingRequest request);

    AiExecutionMetrics getLatestMetrics();
}
