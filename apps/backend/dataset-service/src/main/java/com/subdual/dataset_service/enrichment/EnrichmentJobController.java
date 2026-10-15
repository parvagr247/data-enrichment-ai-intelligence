package com.subdual.dataset_service.enrichment;

import com.subdual.dataset_service.enrichment.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.enrichment.dto.SingleEnrichmentRequest;
import com.subdual.dataset_service.enrichment.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.enrichment.dto.RowEnrichmentResult;
import com.subdual.dataset_service.enrichment.DatasetEnrichmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/enrichment")
@RequiredArgsConstructor
public class EnrichmentJobController {

    private final DatasetEnrichmentService datasetEnrichmentService;

    @PostMapping(value = "/jobs", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<EnrichmentJobResponse> submitJob(
            @Valid @RequestBody EnrichmentJobRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        EnrichmentJobResponse response = datasetEnrichmentService.createAndSubmitJob(request, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping(value = "/jobs/{jobId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EnrichmentJobResponse> getJob(
            @PathVariable String jobId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        return datasetEnrichmentService.getJob(jobId, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeJobEvents(
            @PathVariable String jobId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        return datasetEnrichmentService.subscribeJobEvents(jobId, userId);
    }

    @PostMapping(value = "/jobs/{jobId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> cancelJob(
            @PathVariable String jobId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        boolean cancelled = datasetEnrichmentService.cancelJob(jobId, userId);
        return cancelled ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping(value = "/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EnrichmentJobResponse>> listJobs(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        return ResponseEntity.ok(datasetEnrichmentService.listJobs(userId));
    }

    @PostMapping(value = "/single", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RowEnrichmentResult> enrichSingle(
            @Valid @RequestBody SingleEnrichmentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        RowEnrichmentResult response = (userId != null && !userId.isBlank())
                ? datasetEnrichmentService.enrichSingle(request, userId)
                : datasetEnrichmentService.enrichSingle(request);
        return ResponseEntity.ok(response);
    }
}
