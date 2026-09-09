package com.subdual.dataset_service.enrichment.service.helper;

import com.subdual.dataset_service.enrichment.api.dto.request.EnrichmentJobRequest;
import com.subdual.dataset_service.enrichment.api.dto.response.EnrichmentJobResponse;
import com.subdual.dataset_service.enrichment.api.dto.response.ExecutionEvent;
import com.subdual.dataset_service.enrichment.api.dto.response.RowEnrichmentResult;
import com.subdual.dataset_service.enrichment.model.JobState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class EnrichmentJobManager {

    private final Map<String, JobState> activeJobs = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> jobEmitters = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionEvent>> jobEventHistory = new ConcurrentHashMap<>();

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
            log.info("Cancelled dataset enrichment job '{}' by user '{}'", jobId, userId);

            emitExecutionEvent(jobId, jobId + "-cancel", -1, "Batch Job", "CANCELLED", "CANCELLED", "system", "Enrichment run cancelled by user", Map.of());
            emitJobCompleted(jobId, "CANCELLED", state.completedRows.get(), state.failedRows.get(), state.durationMs != null ? state.durationMs : 0L);
            return true;
        }
        return false;
    }

    public SseEmitter subscribeJobEvents(String jobId, String userId, int concurrency) {
        JobState state = activeJobs.get(jobId);
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10 minutes timeout

        if (state == null || (userId != null && !userId.isBlank() && state.userId != null && !state.userId.equals(userId))) {
            rejectSubscription(emitter, jobId);
            return emitter;
        }

        Runnable cleanup = configureEmitterLifecycle(emitter, jobId);
        jobEmitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        try {
            sendInitialHandshake(emitter, jobId, state, concurrency);
            replayEventHistory(emitter, jobId);
            handleTerminalJobState(emitter, jobId, state, cleanup);
        } catch (Exception ex) {
            log.warn("Error sending initial SSE handshake for job {}: {}", jobId, ex.getMessage());
            cleanup.run();
        }

        return emitter;
    }

    private void rejectSubscription(SseEmitter emitter, String jobId) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", "Job not found or access denied: " + jobId)));
            emitter.complete();
        } catch (Exception ignored) {}
    }

    private Runnable configureEmitterLifecycle(SseEmitter emitter, String jobId) {
        Runnable cleanup = () -> {
            List<SseEmitter> list = jobEmitters.get(jobId);
            if (list != null) {
                list.remove(emitter);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            emitter.complete();
            cleanup.run();
        });
        emitter.onError(e -> cleanup.run());
        return cleanup;
    }

    private void sendInitialHandshake(SseEmitter emitter, String jobId, JobState state, int concurrency) throws Exception {
        emitter.send(SseEmitter.event().name("init").data(Map.of(
                "jobId", jobId,
                "datasetName", state.datasetName != null ? state.datasetName : "",
                "concurrency", concurrency,
                "totalRows", state.totalRows,
                "completedRows", state.completedRows.get(),
                "failedRows", state.failedRows.get(),
                "status", state.status
        )));
    }

    private void replayEventHistory(SseEmitter emitter, String jobId) throws Exception {
        List<ExecutionEvent> history = jobEventHistory.get(jobId);
        if (history != null) {
            synchronized (history) {
                for (ExecutionEvent ev : history) {
                    emitter.send(SseEmitter.event().name("execution-event").data(ev));
                }
            }
        }
    }

    private void handleTerminalJobState(SseEmitter emitter, String jobId, JobState state, Runnable cleanup) throws Exception {
        if ("COMPLETED".equalsIgnoreCase(state.status) || "FAILED".equalsIgnoreCase(state.status) || "CANCELLED".equalsIgnoreCase(state.status)) {
            emitter.send(SseEmitter.event().name("job-completed").data(Map.of(
                    "jobId", jobId,
                    "status", state.status,
                    "completedRows", state.completedRows.get(),
                    "failedRows", state.failedRows.get(),
                    "durationMs", state.durationMs != null ? state.durationMs : 0L
            )));
            emitter.complete();
            cleanup.run();
        }
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
        if (jobId == null) {
            return;
        }

        ExecutionEvent event = new ExecutionEvent(
                jobId, rowId, rowIndex, entity, status, stage, workerId, message, Instant.now().toString(), metadata
        );

        recordEventHistory(jobId, event);
        broadcastEventToEmitters(jobId, event);
    }

    private void recordEventHistory(String jobId, ExecutionEvent event) {
        List<ExecutionEvent> history = jobEventHistory.computeIfAbsent(jobId, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (history) {
            if (history.size() >= 500) {
                history.remove(0);
            }
            history.add(event);
        }
    }

    private void broadcastEventToEmitters(String jobId, ExecutionEvent event) {
        List<SseEmitter> emitters = jobEmitters.get(jobId);
        if (emitters != null && !emitters.isEmpty()) {
            List<SseEmitter> deadEmitters = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("execution-event")
                            .data(event));
                } catch (Exception ex) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }

    public void emitJobCompleted(String jobId, String status, int completed, int failed, Long durationMs) {
        List<SseEmitter> emitters = jobEmitters.get(jobId);
        if (emitters != null && !emitters.isEmpty()) {
            Map<String, Object> data = Map.of(
                    "jobId", jobId,
                    "status", status,
                    "completedRows", completed,
                    "failedRows", failed,
                    "durationMs", durationMs != null ? durationMs : 0L
            );
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("job-completed")
                            .data(data));
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
            jobEmitters.remove(jobId);
        }
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
