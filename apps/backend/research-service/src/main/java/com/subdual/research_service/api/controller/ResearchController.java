package com.subdual.research_service.api.controller;

import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.api.dto.response.ResearchJobResponse;
import com.subdual.research_service.api.dto.response.ResearchResponse;
import com.subdual.research_service.research.job.ResearchJobService;
import com.subdual.research_service.research.service.ResearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/research")
@RequiredArgsConstructor
@Slf4j
public class ResearchController {

    private final ResearchService researchService;
    private final ResearchJobService researchJobService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResearchResponse> executeResearch(@Valid @RequestBody ResearchRequest request) {
        log.info("Received synchronous research request for URL='{}'", request != null ? request.url() : null);
        ResearchResponse response = researchService.executeResearch(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ResearchJobResponse> submitJob(
            @Valid @RequestBody ResearchRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        log.info("Received asynchronous research job request for URL='{}' from user='{}'", request != null ? request.url() : null, userId);
        ResearchJobResponse response = (userId != null && !userId.isBlank())
                ? researchJobService.submitJob(request, userId)
                : researchJobService.submitJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping(value = "/jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResearchJobResponse> getJob(
            @PathVariable String jobId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        Optional<ResearchJobResponse> opt = (userId != null && !userId.isBlank())
                ? researchJobService.getJob(jobId, userId)
                : researchJobService.getJob(jobId);
        return opt.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
