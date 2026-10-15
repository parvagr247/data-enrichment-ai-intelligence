package com.subdual.dataset_service.enrichment;

import com.subdual.dataset_service.enrichment.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.enrichment.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.enrichment.dto.RowEnrichmentResult;
import com.subdual.dataset_service.enrichment.event.JobEventBroadcaster;
import com.subdual.dataset_service.enrichment.model.JobState;
import com.subdual.dataset_service.enrichment.event.DefaultJobEventBroadcaster;
import com.subdual.dataset_service.enrichment.event.SseEventDeliveryChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cohesive manager for enrichment job states and lifecycle transitions.
 * Implements Singleton Pattern via thread-safe static holder alongside Spring managed bean.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EnrichmentJobManager {

    private static class InstanceHolder {
        private static final EnrichmentJobManager INSTANCE = new EnrichmentJobManager();
    }

    public static EnrichmentJobManager getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private final JobEventBroadcaster eventBroadcaster;
    private final Map<String, JobState> activeJobs = new ConcurrentHashMap<>();

    public EnrichmentJobManager() {
        this(new DefaultJobEventBroadcaster(new SseEventDeliveryChannel()));
    }

    public JobState createJobState(EnrichmentJobRequest request, String userId, RowIdentityResolver rowIdentityResolver) {
        String jobId = UUID.randomUUID().toString();
        int totalRows = request.rows() != null ? request.rows().size() : 0;

        JobState state = new JobState();
        state.jobId = jobId;
        state.userId = userId;
        state.datasetName = request.datasetName() != null ? request.datasetName() : "dataset-" + jobId.substring(0, 8);
        state.userRequirement = request.userRequirement();
        state.status = "PROCESSING";
        state.totalRows = totalRows;
        state.progress = 0;
        state.createdAt = Instant.now();

        if (request.rows() != null) {
            for (int i = 0; i < request.rows().size(); i++) {
                Map<String, String> row = request.rows().get(i);
                RowEnrichmentResult initialResult = rowIdentityResolver.buildInitialRowResult(
                        jobId, i, row, request.columnMapping(), request.defaultEntityType()
                );
                state.rowResultsMap.put(i, initialResult);
            }
        }

        activeJobs.put(jobId, state);
        return state;
    }

    public JobState getJobState(String jobId) {
        return activeJobs.get(jobId);
    }

    public Optional<EnrichmentJobResponse> getJob(String jobId, String userId, int concurrency) {
        JobState state = activeJobs.get(jobId);
        if (state == null) {
            return Optional.empty();
        }
        if (userId != null && !userId.isBlank() && state.userId != null && !state.userId.equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(toJobResponse(state, concurrency));
    }

    public List<EnrichmentJobResponse> listJobs(String userId, int concurrency) {
        return activeJobs.values().stream()
                .filter(state -> userId == null || userId.isBlank() || state.userId == null || state.userId.equals(userId))
                .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                .map(s -> toJobResponse(s, concurrency))
                .toList();
    }

    public boolean cancelJob(String jobId, String userId) {
        JobState state = activeJobs.get(jobId);
        if (state != null && "PROCESSING".equalsIgnoreCase(state.status)) {
            if (userId != null && !userId.isBlank() && state.userId != null && !state.userId.equals(userId)) {
                return false;
            }
            state.cancelled = true;
            state.status = "CANCELLED";
            state.completedAt = Instant.now();

            emitExecutionEvent(jobId, jobId + "-cancel", -1, "Batch Job", "CANCELLED", "CANCELLED", "system", "Enrichment run cancelled by user", Map.of());
            emitJobCompleted(jobId, "CANCELLED", state.completedRows.get(), state.failedRows.get(), state.durationMs != null ? state.durationMs : 0L);
            return true;
        }
        return false;
    }

    public SseEmitter subscribeJobEvents(String jobId, String userId, int concurrency) {
        JobState state = activeJobs.get(jobId);
        return eventBroadcaster.subscribe(jobId, state, userId, concurrency);
    }

    public void emitExecutionEvent(
            String jobId,
            String rowId,
            int rowIndex,
            String entity,
            String status,
            String stage,
            String workerId,
            String message,
            Map<String, Object> metadata
    ) {
        eventBroadcaster.broadcastExecutionEvent(jobId, rowId, rowIndex, entity, status, stage, workerId, message, metadata);
    }

    public void emitJobCompleted(String jobId, String status, int completed, int failed, Long durationMs) {
        eventBroadcaster.broadcastJobCompleted(jobId, status, completed, failed, durationMs);
    }

    public EnrichmentJobResponse toJobResponse(JobState s, int concurrency) {
        List<RowEnrichmentResult> sortedResults = s.rowResultsMap.values().stream()
                .sorted(Comparator.comparingInt(RowEnrichmentResult::rowIndex))
                .toList();
        return new EnrichmentJobResponse(
                s.jobId,
                s.datasetName,
                s.status,
                s.userRequirement,
                s.totalRows,
                s.completedRows.get(),
                s.failedRows.get(),
                s.progress,
                s.createdAt != null ? s.createdAt.toString() : null,
                s.completedAt != null ? s.completedAt.toString() : null,
                s.durationMs,
                sortedResults,
                s.errorMessage,
                concurrency,
                s.degradedRows.get(),
                s.partialRows.get()
        );
    }
}
