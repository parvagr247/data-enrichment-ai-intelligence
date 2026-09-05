package com.subdual.research_service.controller;

import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import com.subdual.research_service.service.ResearchService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchController {

    private final ResearchService researchService;

    public ResearchController(ResearchService researchService) {
        this.researchService = researchService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResearchResponse> executeResearch(@Valid @RequestBody ResearchRequest request) {
        ResearchResponse response = researchService.executeResearch(request);
        return ResponseEntity.ok(response);
    }
}
