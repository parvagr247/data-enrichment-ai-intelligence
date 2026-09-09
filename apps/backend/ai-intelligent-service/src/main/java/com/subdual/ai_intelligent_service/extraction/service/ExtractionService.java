package com.subdual.ai_intelligent_service.extraction.service;

import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.response.ExtractionResponse;

public interface ExtractionService {
    ExtractionResponse extractFacts(ExtractionRequest request);
}
