package com.subdual.ai_intelligent_service.extraction.service;

import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.ExtractionResponse;

public interface ExtractionService {
    ExtractionResponse extractFacts(ExtractionRequest request);
}
