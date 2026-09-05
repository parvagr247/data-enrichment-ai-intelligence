package com.subdual.ai_intelligent_service.service;

import com.subdual.ai_intelligent_service.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.dto.ExtractionResponse;

/**
 * Service contract for structured factual extraction from text.
 */
public interface ExtractionService {
    ExtractionResponse extractFacts(ExtractionRequest request);
}
