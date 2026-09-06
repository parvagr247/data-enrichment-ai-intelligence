package com.subdual.dataset_service.controller;

import com.subdual.dataset_service.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.dto.RowEnrichmentResult;
import com.subdual.dataset_service.dto.SingleEnrichmentRequest;
import com.subdual.dataset_service.service.DatasetEnrichmentService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/enrichment")
@RequiredArgsConstructor
@Slf4j
public class EnrichmentJobController {

    private final DatasetEnrichmentService datasetEnrichmentService;

    @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<EnrichmentJobResponse> submitJob(@Valid @RequestBody EnrichmentJobRequest request) {
        log.info("Received dataset enrichment job submission for '{}' ({} rows)",
                request != null ? request.datasetName() : "unknown",
                request != null && request.rows() != null ? request.rows().size() : 0);
        EnrichmentJobResponse response = datasetEnrichmentService.createAndSubmitJob(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping(value = "/jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrichmentJobResponse> getJob(@PathVariable String jobId) {
        return datasetEnrichmentService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribeJobEvents(@PathVariable String jobId) {
        log.info("Client subscribed to SSE events for enrichment job '{}'", jobId);
        return datasetEnrichmentService.subscribeJobEvents(jobId);
    }

    @PostMapping(value = "/jobs/{jobId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cancelJob(@PathVariable String jobId) {
        boolean cancelled = datasetEnrichmentService.cancelJob(jobId);
        return cancelled ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EnrichmentJobResponse>> listJobs() {
        return ResponseEntity.ok(datasetEnrichmentService.listJobs());
    }

    @PostMapping(value = "/single", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RowEnrichmentResult> enrichSingle(@Valid @RequestBody SingleEnrichmentRequest request) {
        log.info("Executing synchronous single row enrichment");
        RowEnrichmentResult response = datasetEnrichmentService.enrichSingle(request);
        return ResponseEntity.ok(response);
    }
}
