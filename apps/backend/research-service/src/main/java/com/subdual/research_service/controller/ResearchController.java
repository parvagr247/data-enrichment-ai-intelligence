package com.subdual.research_service.controller;

import com.subdual.research_service.dto.response.ResearchJobResponse;
import com.subdual.research_service.dto.request.ResearchRequest;
import com.subdual.research_service.dto.response.ResearchResponse;
import com.subdual.research_service.service.InMemoryResearchJobService;
import com.subdual.research_service.service.ResearchJobService;
import com.subdual.research_service.service.ResearchService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchController {

    private final ResearchService researchService;
    private final ResearchJobService researchJobService;

    @Autowired
    public ResearchController(ResearchService researchService, ResearchJobService researchJobService) {
        this.researchService = researchService;
        this.researchJobService = researchJobService;
    }

    /**
     * Backward-compatible constructor for testing without an explicit job service bean.
     */
    public ResearchController(ResearchService researchService) {
        this(researchService, new InMemoryResearchJobService(researchService));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResearchResponse> executeResearch(@Valid @RequestBody ResearchRequest request) {
        ResearchResponse response = researchService.executeResearch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ResearchJobResponse> submitJob(@Valid @RequestBody ResearchRequest request) {
        ResearchJobResponse response = researchJobService.submitJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping(value = "/jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResearchJobResponse> getJob(@PathVariable String jobId) {
        return researchJobService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
