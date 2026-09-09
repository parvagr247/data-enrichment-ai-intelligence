package com.subdual.dataset_service.enrichment.service;

import com.subdual.dataset_service.enrichment.api.dto.request.EnrichmentJobRequest;
import com.subdual.dataset_service.enrichment.api.dto.request.SingleEnrichmentRequest;
import com.subdual.dataset_service.enrichment.api.dto.response.EnrichmentJobResponse;
import com.subdual.dataset_service.enrichment.api.dto.response.RowEnrichmentResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

public interface DatasetEnrichmentService {

    default EnrichmentJobResponse createAndSubmitJob(EnrichmentJobRequest request) {
        return createAndSubmitJob(request, null);
    }

    EnrichmentJobResponse createAndSubmitJob(EnrichmentJobRequest request, String userId);

    default Optional<EnrichmentJobResponse> getJob(String jobId) {
        return getJob(jobId, null);
    }

    Optional<EnrichmentJobResponse> getJob(String jobId, String userId);

    default List<EnrichmentJobResponse> listJobs() {
        return listJobs(null);
    }

    List<EnrichmentJobResponse> listJobs(String userId);

    default RowEnrichmentResult enrichSingle(SingleEnrichmentRequest request) {
        return enrichSingle(request, null);
    }

    RowEnrichmentResult enrichSingle(SingleEnrichmentRequest request, String userId);

    default boolean cancelJob(String jobId) {
        return cancelJob(jobId, null);
    }

    boolean cancelJob(String jobId, String userId);

    default SseEmitter subscribeJobEvents(String jobId) {
        return subscribeJobEvents(jobId, null);
    }

    SseEmitter subscribeJobEvents(String jobId, String userId);
}
