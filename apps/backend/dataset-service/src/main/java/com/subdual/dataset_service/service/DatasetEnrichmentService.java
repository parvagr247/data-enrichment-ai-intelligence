package com.subdual.dataset_service.service;

import com.subdual.dataset_service.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.dto.RowEnrichmentResult;
import com.subdual.dataset_service.dto.SingleEnrichmentRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;

public interface DatasetEnrichmentService {

    EnrichmentJobResponse createAndSubmitJob(EnrichmentJobRequest request);

    Optional<EnrichmentJobResponse> getJob(String jobId);

    List<EnrichmentJobResponse> listJobs();

    RowEnrichmentResult enrichSingle(SingleEnrichmentRequest request);

    boolean cancelJob(String jobId);

    SseEmitter subscribeJobEvents(String jobId);
}
