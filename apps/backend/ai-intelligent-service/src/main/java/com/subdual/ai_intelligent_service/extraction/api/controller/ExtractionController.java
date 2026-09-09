package com.subdual.ai_intelligent_service.extraction.api.controller;

import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.api.dto.response.ExtractionResponse;
import com.subdual.ai_intelligent_service.extraction.service.ExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionService extractionService;

    @PostMapping(value = "/extract", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExtractionResponse> extract(@Valid @RequestBody ExtractionRequest request) {
        ExtractionResponse response = extractionService.extractFacts(request);
        return ResponseEntity.ok(response);
    }
}
