package com.subdual.dataset_service.service;

import com.subdual.dataset_service.dto.EntityAttributeDto;
import com.subdual.dataset_service.dto.EntitySourceDto;
import com.subdual.dataset_service.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.dto.ExecutionEvent;
import com.subdual.dataset_service.dto.FactEvidenceDto;
import com.subdual.dataset_service.dto.PersistEntityRequest;
import com.subdual.dataset_service.dto.ProfileAssessmentRequest;
import com.subdual.dataset_service.dto.ProfileAssessmentResponse;
import com.subdual.dataset_service.dto.RowEnrichmentResult;
import com.subdual.dataset_service.dto.SingleEnrichmentRequest;
import com.subdual.dataset_service.profile.model.ObjectiveAssessment;
import com.subdual.dataset_service.profile.model.RecommendedApproach;
import com.subdual.dataset_service.profile.model.ResearchFinding;
import com.subdual.dataset_service.profile.model.ResearchObjective;
import com.subdual.dataset_service.profile.model.ResearchProfile;
import com.subdual.dataset_service.integration.client.AiServiceClient;
import com.subdual.dataset_service.integration.client.ResearchServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
public class DefaultDatasetEnrichmentService implements DatasetEnrichmentService {

    private final ResearchServiceClient researchServiceClient;
    private final AiServiceClient aiServiceClient;
    private final EntityPersistenceService persistenceService;
    private final ExecutorService enrichmentJobExecutor;
    private final com.subdual.dataset_service.service.executor.EnrichmentTaskExecutor enrichmentTaskExecutor;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public DefaultDatasetEnrichmentService(
            ResearchServiceClient researchServiceClient,
            AiServiceClient aiServiceClient,
            EntityPersistenceService persistenceService,
            ExecutorService enrichmentJobExecutor,
            com.subdual.dataset_service.service.executor.EnrichmentTaskExecutor enrichmentTaskExecutor
    ) {
        this(researchServiceClient, aiServiceClient, persistenceService, enrichmentJobExecutor, enrichmentTaskExecutor,
                new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultDatasetEnrichmentService(
            ResearchServiceClient researchServiceClient,
            AiServiceClient aiServiceClient,
            EntityPersistenceService persistenceService,
            ExecutorService enrichmentJobExecutor,
            com.subdual.dataset_service.service.executor.EnrichmentTaskExecutor enrichmentTaskExecutor,
            com.fasterxml.jackson.databind.@org.jspecify.annotations.Nullable ObjectMapper objectMapper
    ) {
        this.researchServiceClient = researchServiceClient;
        this.aiServiceClient = aiServiceClient;
        this.persistenceService = persistenceService;
        this.enrichmentJobExecutor = enrichmentJobExecutor;
        this.enrichmentTaskExecutor = enrichmentTaskExecutor;
        this.objectMapper = objectMapper != null ? objectMapper : new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    private final Map<String, JobState> activeJobs = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> jobEmitters = new ConcurrentHashMap<>();
    private final Map<String, List<ExecutionEvent>> jobEventHistory = new ConcurrentHashMap<>();

    private static class JobState {
        String jobId;
        String userId;
        String datasetName;
        String userRequirement;
        volatile String status;
        int totalRows;
        java.util.concurrent.atomic.AtomicInteger completedRows = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failedRows = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger partialRows = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger degradedRows = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger insufficientEvidenceRows = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger processingRows = new java.util.concurrent.atomic.AtomicInteger(0);
        volatile int progress;
        Instant createdAt;
        Instant completedAt;
        Long durationMs;
        Map<Integer, RowEnrichmentResult> rowResultsMap = new ConcurrentHashMap<>();
        String errorMessage;
        volatile boolean cancelled = false;
    }

    @Override
    public EnrichmentJobResponse createAndSubmitJob(EnrichmentJobRequest request) {
        return createAndSubmitJob(request, null);
    }

    @Override
    public EnrichmentJobResponse createAndSubmitJob(EnrichmentJobRequest request, String userId) {
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
                String firstName = extractMappedValue(row, request.columnMapping(), "firstNameColumn");
                String lastName = extractMappedValue(row, request.columnMapping(), "lastNameColumn");
                String fullName = extractMappedValue(row, request.columnMapping(), "fullNameColumn");
                String name = extractMappedValue(row, request.columnMapping(), "nameColumn");
                String compositeName = buildCompositeName(firstName, lastName, fullName, name);
                String url = extractMappedValue(row, request.columnMapping(), "urlColumn");
                String displayName = (compositeName != null && !compositeName.isBlank()) ? compositeName : (url != null && !url.isBlank() ? url : "Row " + (i + 1));
                state.rowResultsMap.put(i, new RowEnrichmentResult(
                        jobId + "-row-" + i,
                        i,
                        row,
                        "QUEUED",
                        displayName,
                        url != null ? url : "",
                        request.defaultEntityType() != null ? request.defaultEntityType() : "PERSON",
                        Map.of(),
                        List.of(),
                        List.of(),
                        0.0,
                        List.of(),
                        null,
                        "QUEUED",
                        null,
                        "Queued for execution",
                        null,
                        null
                ));
            }
        }

        activeJobs.put(jobId, state);

        enrichmentJobExecutor.submit(() -> executeJobAsync(state, request));

        return toJobResponse(state);
    }

    @Override
    public Optional<EnrichmentJobResponse> getJob(String jobId) {
        return getJob(jobId, null);
    }

    @Override
    public Optional<EnrichmentJobResponse> getJob(String jobId, String userId) {
        JobState state = activeJobs.get(jobId);
        if (state == null) {
            return Optional.empty();
        }
        if (userId != null && !userId.isBlank() && state.userId != null && !state.userId.equals(userId)) {
            // IDOR Protection: User B cannot view User A's job
            return Optional.empty();
        }
        return Optional.of(toJobResponse(state));
    }

    @Override
    public List<EnrichmentJobResponse> listJobs() {
        return listJobs(null);
    }

    @Override
    public List<EnrichmentJobResponse> listJobs(String userId) {
        return activeJobs.values().stream()
                .filter(state -> userId == null || userId.isBlank() || state.userId == null || state.userId.equals(userId))
                .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                .map(this::toJobResponse)
                .toList();
    }

    @Override
    public RowEnrichmentResult enrichSingle(SingleEnrichmentRequest request) {
        return enrichSingle(request, null);
    }

    @Override
    public RowEnrichmentResult enrichSingle(SingleEnrichmentRequest request, String userId) {
        Map<String, String> row = request.row();
        Map<String, String> mapping = request.columnMapping();
        String entityType = request.entityType();
        String requirement = request.userRequirement();

        AiServiceClient.RequirementCallResponse reqResponse = aiServiceClient.interpretRequirement(requirement, entityType, row);
        List<String> targetFields = reqResponse != null ? reqResponse.requestedFields() : List.of();

        return processSingleRow("single-" + System.currentTimeMillis(), 0, row, mapping, entityType, requirement, targetFields, null, "worker-sync", System.currentTimeMillis(), userId);
    }

    @Override
    public boolean cancelJob(String jobId) {
        return cancelJob(jobId, null);
    }

    @Override
    public boolean cancelJob(String jobId, String userId) {
        JobState state = activeJobs.get(jobId);
        if (state != null && "PROCESSING".equalsIgnoreCase(state.status)) {
            if (userId != null && !userId.isBlank() && state.userId != null && !state.userId.equals(userId)) {
                // IDOR Protection: User B cannot cancel User A's job
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

    @Override
    public SseEmitter subscribeJobEvents(String jobId) {
        return subscribeJobEvents(jobId, null);
    }

    @Override
    public SseEmitter subscribeJobEvents(String jobId, String userId) {
        JobState state = activeJobs.get(jobId);
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10 minutes timeout

        if (state == null || (userId != null && !userId.isBlank() && state.userId != null && !state.userId.equals(userId))) {
            try {
                emitter.send(SseEmitter.event().name("error").data(Map.of("message", "Job not found or access denied: " + jobId)));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

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

        jobEmitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        try {
            // Handshake with job initial configuration
            emitter.send(SseEmitter.event().name("init").data(Map.of(
                    "jobId", jobId,
                    "datasetName", state.datasetName != null ? state.datasetName : "",
                    "concurrency", enrichmentTaskExecutor.getConcurrency(),
                    "totalRows", state.totalRows,
                    "completedRows", state.completedRows.get(),
                    "failedRows", state.failedRows.get(),
                    "status", state.status
            )));

            // Replay existing events in order for newly connected or reconnected client
            List<ExecutionEvent> history = jobEventHistory.get(jobId);
            if (history != null) {
                synchronized (history) {
                    for (ExecutionEvent ev : history) {
                        emitter.send(SseEmitter.event().name("execution-event").data(ev));
                    }
                }
            }

            // If job is already in a terminal state, inform client and close
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
        } catch (Exception ex) {
            log.warn("Error sending initial SSE handshake for job {}: {}", jobId, ex.getMessage());
            cleanup.run();
        }

        return emitter;
    }

    private void emitExecutionEvent(
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
                jobId,
                rowId,
                rowIndex,
                entity,
                status,
                stage,
                workerId,
                message,
                Instant.now().toString(),
                metadata
        );

        // Record bounded history (latest 500 events)
        List<ExecutionEvent> history = jobEventHistory.computeIfAbsent(jobId, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (history) {
            if (history.size() >= 500) {
                history.remove(0);
            }
            history.add(event);
        }

        // Broadcast to live SSE emitters
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

    private void emitJobCompleted(String jobId, String status, int completed, int failed, Long durationMs) {
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

    private void executeJobAsync(JobState state, EnrichmentJobRequest request) {
        long startMs = System.currentTimeMillis();
        try {
            log.info("Starting enrichment job '{}' ({} rows, concurrency={}), requirement='{}'",
                    state.jobId, state.totalRows, enrichmentTaskExecutor.getConcurrency(), state.userRequirement);

            AiServiceClient.RequirementCallResponse reqResponse = aiServiceClient.interpretRequirement(
                    request.userRequirement(),
                    request.defaultEntityType(),
                    request.rows() != null && !request.rows().isEmpty() ? request.rows().get(0) : Map.of()
            );
            List<String> targetFields = reqResponse != null ? reqResponse.requestedFields() : List.of();

            List<Map<String, String>> rows = request.rows() != null ? request.rows() : List.of();
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < rows.size(); i++) {
                final int rowIndex = i;
                final Map<String, String> row = rows.get(i);
                final String rowId = state.jobId + "-row-" + rowIndex;

                java.util.concurrent.CompletableFuture<Void> future = enrichmentTaskExecutor.submitTask(() -> {
                    if (state.cancelled) {
                        return null;
                    }
                    state.processingRows.incrementAndGet();

                    // Derive worker identifier from thread name or pool index
                    String rawThread = Thread.currentThread().getName();
                    String workerId;
                    if (rawThread.contains("worker-")) {
                        workerId = "worker-" + rawThread.substring(rawThread.lastIndexOf("worker-") + 7);
                    } else {
                        workerId = "worker-" + (rowIndex % enrichmentTaskExecutor.getConcurrency() + 1);
                    }

                    RowEnrichmentResult initial = state.rowResultsMap.get(rowIndex);
                    String entityName = initial != null ? initial.displayName() : "Row " + (rowIndex + 1);
                    long startedAtMs = System.currentTimeMillis();

                    state.rowResultsMap.put(rowIndex, new RowEnrichmentResult(
                            rowId,
                            rowIndex,
                            row,
                            "PROCESSING",
                            entityName,
                            initial != null ? initial.canonicalUrl() : "",
                            request.defaultEntityType() != null ? request.defaultEntityType() : "PERSON",
                            Map.of(),
                            List.of(),
                            List.of(),
                            0.0,
                            List.of(),
                            null,
                            "DISCOVERING",
                            workerId,
                            "Resolving entity identity & discovering sources...",
                            startedAtMs,
                            null
                    ));

                    emitExecutionEvent(
                            state.jobId,
                            rowId,
                            rowIndex,
                            entityName,
                            "PROCESSING",
                            "DISCOVERING",
                            workerId,
                            "Resolving entity identity & discovering sources...",
                            Map.of("originalData", row)
                    );

                    try {
                        RowEnrichmentResult result = processSingleRow(
                                rowId,
                                rowIndex,
                                row,
                                request.columnMapping(),
                                request.defaultEntityType(),
                                request.userRequirement(),
                                targetFields,
                                state.jobId,
                                workerId,
                                startedAtMs,
                                state.userId
                        );
                        state.rowResultsMap.put(rowIndex, result);

                        if ("FAILED".equalsIgnoreCase(result.status())) {
                            state.failedRows.incrementAndGet();
                            emitExecutionEvent(
                                    state.jobId,
                                    rowId,
                                    rowIndex,
                                    result.displayName(),
                                    "FAILED",
                                    "FAILED",
                                    workerId,
                                    result.errorMessage() != null ? result.errorMessage() : "Row enrichment failed",
                                    Map.of("error", result.errorMessage() != null ? result.errorMessage() : "Unknown error")
                            );
                        } else {
                            state.completedRows.incrementAndGet();
                            if ("PARTIAL".equalsIgnoreCase(result.status())) {
                                state.partialRows.incrementAndGet();
                            } else if ("AI_DEGRADED".equalsIgnoreCase(result.status())) {
                                state.degradedRows.incrementAndGet();
                            } else if ("INSUFFICIENT_EVIDENCE".equalsIgnoreCase(result.status())) {
                                state.insufficientEvidenceRows.incrementAndGet();
                            }
                            emitExecutionEvent(
                                    state.jobId,
                                    rowId,
                                    rowIndex,
                                    result.displayName(),
                                    result.status(),
                                    result.status(),
                                    workerId,
                                    result.message() != null ? result.message() : "Enrichment completed",
                                    Map.of(
                                            "attributesCount", result.attributes() != null ? result.attributes().size() : 0,
                                            "sourcesCount", result.sources() != null ? result.sources().size() : 0,
                                            "confidence", result.confidence(),
                                            "result", result
                                    )
                            );
                        }
                    } catch (Exception ex) {
                        log.error("Failed enriching row {} in job {}: {}", rowIndex, state.jobId, ex.getMessage());
                        state.failedRows.incrementAndGet();
                        RowEnrichmentResult failedResult = new RowEnrichmentResult(
                                rowId,
                                rowIndex,
                                row,
                                "FAILED",
                                entityName,
                                "",
                                request.defaultEntityType(),
                                Map.of(),
                                targetFields,
                                List.of(),
                                0.0,
                                List.of(),
                                ex.getMessage(),
                                "FAILED",
                                workerId,
                                "Failed: " + ex.getMessage(),
                                startedAtMs,
                                System.currentTimeMillis()
                        );
                        state.rowResultsMap.put(rowIndex, failedResult);

                        emitExecutionEvent(
                                state.jobId,
                                rowId,
                                rowIndex,
                                entityName,
                                "FAILED",
                                "FAILED",
                                workerId,
                                "Failed: " + ex.getMessage(),
                                Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Exception during execution")
                        );
                    } finally {
                        state.processingRows.decrementAndGet();
                        int processed = state.completedRows.get() + state.failedRows.get();
                        state.progress = (int) Math.round(((double) processed / Math.max(1, state.totalRows)) * 100);
                    }
                    return null;
                });
                futures.add(future);
            }

            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

            state.completedAt = Instant.now();
            state.durationMs = System.currentTimeMillis() - startMs;

            if (state.cancelled) {
                state.status = "CANCELLED";
            } else {
                int failed = state.failedRows.get();
                state.status = (failed == state.totalRows && state.totalRows > 0) ? "FAILED" : "COMPLETED";
            }

            log.info("Completed enrichment job '{}' in {} ms (completed={}, partial={}, failed={})",
                    state.jobId, state.durationMs, state.completedRows.get(), state.partialRows.get(), state.failedRows.get());

            emitJobCompleted(state.jobId, state.status, state.completedRows.get(), state.failedRows.get(), state.durationMs);

        } catch (Exception ex) {
            log.error("Fatal error during enrichment job {}: {}", state.jobId, ex.getMessage(), ex);
            state.status = "FAILED";
            state.errorMessage = ex.getMessage();
            state.completedAt = Instant.now();
            state.durationMs = System.currentTimeMillis() - startMs;
            emitJobCompleted(state.jobId, "FAILED", state.completedRows.get(), state.failedRows.get(), state.durationMs);
        }
    }

    private RowEnrichmentResult processSingleRow(
            String rowId,
            int rowIndex,
            Map<String, String> rawRow,
            Map<String, String> mapping,
            String defaultEntityType,
            String requirement,
            List<String> targetFields,
            String jobId,
            String workerId,
            long startedAtMs,
            String explicitUserId
    ) {
        String firstName = extractMappedValue(rawRow, mapping, "firstNameColumn");
        String lastName = extractMappedValue(rawRow, mapping, "lastNameColumn");
        String fullName = extractMappedValue(rawRow, mapping, "fullNameColumn");
        String name = extractMappedValue(rawRow, mapping, "nameColumn");
        String compositeName = buildCompositeName(firstName, lastName, fullName, name);

        String url = extractMappedValue(rawRow, mapping, "urlColumn");
        String org = extractMappedValue(rawRow, mapping, "organizationColumn");
        String role = extractMappedValue(rawRow, mapping, "roleColumn");
        String email = extractMappedValue(rawRow, mapping, "emailColumn");
        String location = extractMappedValue(rawRow, mapping, "locationColumn");

        String rawType = extractMappedValue(rawRow, mapping, "entityTypeColumn");
        String entityType = (rawType != null && !rawType.isBlank()) ? rawType.trim().toUpperCase(Locale.ROOT) : defaultEntityType;

        if ((compositeName == null || compositeName.isBlank()) && (url == null || url.isBlank())) {
            return new RowEnrichmentResult(
                    rowId,
                    rowIndex,
                    rawRow,
                    "FAILED",
                    "Missing Identifier",
                    "",
                    entityType,
                    Map.of(),
                    targetFields,
                    List.of(),
                    0.0,
                    List.of(),
                    "Row missing required name or url identifier",
                    "FAILED",
                    workerId,
                    "Row missing required name or url identifier",
                    startedAtMs,
                    System.currentTimeMillis()
            );
        }

        String displayName = (compositeName != null && !compositeName.isBlank()) ? compositeName : "Unknown";
        String canonicalUrl = url != null ? url : "";

        log.info("[Pipeline: IDENTITY] Row #{} Constructed identity: fullName='{}', firstName='{}', lastName='{}', profileUrl='{}', organization='{}', role='{}', email='{}', location='{}'",
                rowIndex, displayName, firstName, lastName, canonicalUrl, org, role, email, location);

        // 1. Discover Sources via Research Service
        emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "DISCOVERING",
                workerId,
                "Querying verified web sources & search indexes...",
                Map.of("name", displayName, "url", canonicalUrl)
        );

        ResearchServiceClient.ResearchCallResponse researchResp = null;
        String researchError = null;
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (rawRow != null) {
                metadata.putAll(rawRow);
            }
            if (firstName != null && !firstName.isBlank()) metadata.put("firstName", firstName);
            if (lastName != null && !lastName.isBlank()) metadata.put("lastName", lastName);
            if (fullName != null && !fullName.isBlank()) metadata.put("fullName", fullName);
            if (email != null && !email.isBlank()) metadata.put("email", email);
            if (location != null && !location.isBlank()) metadata.put("location", location);

            researchResp = researchServiceClient.executeResearch(new ResearchServiceClient.ResearchCallRequest(
                    url,
                    entityType,
                    displayName,
                    org,
                    role,
                    targetFields,
                    requirement,
                    metadata,
                    firstName,
                    lastName,
                    fullName != null ? fullName : displayName,
                    email,
                    location
            ));
        } catch (Exception ex) {
            log.warn("Research call failed for row {}: {}", rowIndex, ex.getMessage());
            researchError = ex.getMessage();
        }

        if (researchResp == null) {
            return new RowEnrichmentResult(
                    rowId,
                    rowIndex,
                    rawRow,
                    "FAILED",
                    displayName,
                    canonicalUrl,
                    entityType,
                    Map.of(),
                    targetFields,
                    List.of(),
                    0.0,
                    List.of(),
                    "Research service error: " + (researchError != null ? researchError : "No response"),
                    "FAILED",
                    workerId,
                    "Research service failed: " + (researchError != null ? researchError : "No response"),
                    startedAtMs,
                    System.currentTimeMillis()
            );
        }

        if (researchResp.result() != null && researchResp.result().displayName() != null) {
            displayName = researchResp.result().displayName();
        }
        if (researchResp.result() != null && researchResp.result().canonicalUrl() != null) {
            canonicalUrl = researchResp.result().canonicalUrl();
        }

        // 2. Extract Evidence & Sources from Research
        Map<String, AiServiceClient.FactEvidenceCallDto> evidenceMap = new LinkedHashMap<>();
        Map<String, FactEvidenceDto> profileEvidenceMap = new LinkedHashMap<>();
        List<String> sourceUrls = new ArrayList<>();
        List<String> sourceSnippets = new ArrayList<>();
        List<EntitySourceDto> entitySources = new ArrayList<>();

        if (researchResp != null && researchResp.sources() != null) {
            for (ResearchServiceClient.SourceItemDto s : researchResp.sources()) {
                sourceUrls.add(s.url());
                if (s.snippet() != null && !s.snippet().isBlank()) {
                    sourceSnippets.add(s.snippet());
                }
                entitySources.add(new EntitySourceDto(
                        s.url(),
                        s.title(),
                        s.snippet(),
                        s.sourceType(),
                        s.domain(),
                        s.provider(),
                        s.relevance(),
                        parseInstantSafe(s.retrievedAt())
                ));
            }
        }

        if (researchResp != null && researchResp.result() != null && researchResp.result().attributes() != null) {
            for (Map.Entry<String, ResearchServiceClient.EvidenceTupleDto> entry : researchResp.result().attributes().entrySet()) {
                ResearchServiceClient.EvidenceTupleDto tuple = entry.getValue();
                evidenceMap.put(entry.getKey(), new AiServiceClient.FactEvidenceCallDto(
                        entry.getKey(),
                        tuple.value(),
                        tuple.sourceUrl(),
                        tuple.evidenceSnippet(),
                        tuple.confidence(),
                        tuple.corroboratingSources(),
                        tuple.conflictDetected()
                ));
                profileEvidenceMap.put(entry.getKey(), new FactEvidenceDto(
                        entry.getKey(),
                        tuple.value(),
                        tuple.sourceUrl(),
                        tuple.evidenceSnippet(),
                        tuple.confidence(),
                        tuple.corroboratingSources(),
                        tuple.conflictDetected()
                ));
            }
        }

        emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "COLLECTING_SOURCES",
                workerId,
                "Collected and deduplicated " + sourceUrls.size() + " candidate sources...",
                Map.of("sourcesCount", sourceUrls.size())
        );

        emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "EXTRACTING_EVIDENCE",
                workerId,
                "Extracting grounded evidence from " + sourceUrls.size() + " discovered sources...",
                Map.of("sourcesCount", sourceUrls.size(), "evidenceCount", evidenceMap.size())
        );

        // 3. AI Enrichment
        emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "AI_ENRICHMENT",
                workerId,
                "Synthesizing grounded attributes with AI intelligence...",
                Map.of("evidenceCount", evidenceMap.size(), "targetFields", targetFields)
        );

        AiServiceClient.SynthesisCallResponse aiResp = null;
        try {
            aiResp = aiServiceClient.synthesizeEnrichment(new AiServiceClient.SynthesisCallRequest(
                    rawRow,
                    displayName,
                    entityType,
                    canonicalUrl,
                    requirement,
                    targetFields,
                    evidenceMap,
                    sourceUrls
            ));
        } catch (Exception ex) {
            log.warn("AI synthesis call failed for row {}: {}", rowIndex, ex.getMessage());
        }

        // Assemble final attributes
        Map<String, EntityAttributeDto> finalAttributes = new LinkedHashMap<>();
        List<String> unresolvedFields = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        double confidence = 0.85;

        if (aiResp != null && aiResp.attributes() != null && !aiResp.attributes().isEmpty()) {
            displayName = aiResp.displayName();
            confidence = aiResp.overallConfidence();
            unresolvedFields.addAll(aiResp.unresolvedFields());
            conflicts.addAll(aiResp.conflicts());

            for (Map.Entry<String, AiServiceClient.AttributeResultCallDto> entry : aiResp.attributes().entrySet()) {
                AiServiceClient.AttributeResultCallDto attr = entry.getValue();
                finalAttributes.put(entry.getKey(), new EntityAttributeDto(
                        attr.value(),
                        (attr.sources() != null && !attr.sources().isEmpty()) ? attr.sources().get(0) : null,
                        attr.evidence(),
                        attr.confidence()
                ));
            }
        } else {
            // Fallback directly from research evidence
            for (Map.Entry<String, AiServiceClient.FactEvidenceCallDto> entry : evidenceMap.entrySet()) {
                AiServiceClient.FactEvidenceCallDto fact = entry.getValue();
                finalAttributes.put(entry.getKey(), new EntityAttributeDto(
                        fact.value(),
                        fact.sourceUrl(),
                        fact.evidenceSnippet(),
                        fact.confidence()
                ));
                if ("UNKNOWN".equalsIgnoreCase(fact.value())) {
                    unresolvedFields.add(entry.getKey());
                }
            }
        }

        // 4. Assess Objective
        emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "ASSESSING",
                workerId,
                "Evaluating profile relevance against research objective...",
                Map.of("objective", requirement != null ? requirement : "")
        );

        ResearchObjective objectiveObj = ResearchObjective.from(requirement);
        ProfileAssessmentResponse profileAssessment = null;
        try {
            profileAssessment = aiServiceClient.assessProfile(new ProfileAssessmentRequest(
                    rawRow,
                    displayName,
                    canonicalUrl,
                    entityType,
                    objectiveObj,
                    profileEvidenceMap,
                    sourceUrls,
                    sourceSnippets
            ));
        } catch (Exception ex) {
            log.warn("AI profile assessment call failed for row {}: {}", rowIndex, ex.getMessage());
        }

        ResearchProfile profile;
        ObjectiveAssessment assessment;
        RecommendedApproach recommendation;
        List<ResearchFinding> findings;

        if (profileAssessment != null) {
            profile = profileAssessment.profile();
            assessment = profileAssessment.assessment();
            recommendation = profileAssessment.recommendation();
            findings = profileAssessment.findings() != null ? profileAssessment.findings() : List.of();
            if (profile != null) {
                if (!"UNKNOWN".equals(profile.currentRole()) && !finalAttributes.containsKey("currentRole")) {
                    finalAttributes.put("currentRole", new EntityAttributeDto(profile.currentRole(), canonicalUrl, "Extracted from profile", "HIGH"));
                }
                if (!"UNKNOWN".equals(profile.currentOrganization()) && !finalAttributes.containsKey("currentOrganization")) {
                    finalAttributes.put("currentOrganization", new EntityAttributeDto(profile.currentOrganization(), canonicalUrl, "Extracted from profile", "HIGH"));
                }
                if (!"UNKNOWN".equals(profile.location()) && !finalAttributes.containsKey("location")) {
                    finalAttributes.put("location", new EntityAttributeDto(profile.location(), canonicalUrl, "Extracted from profile", "HIGH"));
                }
            }
        } else {
            // Deterministic local fallback if AI service assessment endpoint is unreachable
            boolean hasObj = !objectiveObj.isBlank();
            int score = hasObj ? 50 : 0;
            ObjectiveAssessment.PriorityTier tier = hasObj ? ObjectiveAssessment.PriorityTier.MEDIUM : ObjectiveAssessment.PriorityTier.NONE;
            String whyRel = hasObj ? "Profile identified during enrichment matching basic criteria." : "General profile research completed (no specific objective specified).";

            assessment = new ObjectiveAssessment(score, tier, whyRel, Map.of(), List.of(), List.of());
            recommendation = new RecommendedApproach(
                    RecommendedApproach.ApproachType.NETWORKING_CONVERSATION,
                    "Professional networking outreach",
                    "Ground outreach in verified background.",
                    List.of("Connect referencing current professional role.")
            );
            profile = new ResearchProfile(
                    finalAttributes.containsKey("currentRole") ? finalAttributes.get("currentRole").value() : "UNKNOWN",
                    finalAttributes.containsKey("currentOrganization") ? finalAttributes.get("currentOrganization").value() : "UNKNOWN",
                    finalAttributes.containsKey("location") ? finalAttributes.get("location").value() : "UNKNOWN",
                    displayName + " is a professional identified during enrichment.",
                    "Identified from public professional sources.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
            findings = List.of();
        }

        // 5. Persist canonical entity
        String entityId = (researchResp != null && researchResp.entityId() != null)
                ? researchResp.entityId()
                : UUID.randomUUID().toString();

        emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "PERSISTING",
                workerId,
                "Persisting canonical profile (" + finalAttributes.size() + " attributes) to catalog...",
                Map.of("attributesCount", finalAttributes.size())
        );

        boolean insufficientEvidence = "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(researchResp.status())
                || "NO_SOURCES".equalsIgnoreCase(researchResp.status())
                || "NO_RESULTS".equalsIgnoreCase(researchResp.status())
                || (sourceUrls.isEmpty() && finalAttributes.isEmpty() && !"COMPLETED".equalsIgnoreCase(researchResp.status()));

        boolean aiDegraded = (aiResp == null && profileAssessment == null && !evidenceMap.isEmpty() && (requirement != null && !requirement.isBlank()));

        String status;
        String statusMessage;
        if ("FAILED".equalsIgnoreCase(researchResp.status())) {
            status = "FAILED";
            statusMessage = "Research service failed to locate or resolve entity";
        } else if (insufficientEvidence) {
            status = "INSUFFICIENT_EVIDENCE";
            statusMessage = "Sources returned insufficient grounded evidence";
        } else if (aiDegraded) {
            status = "AI_DEGRADED";
            statusMessage = "Deterministic fallback used; AI intelligence service unavailable or degraded";
        } else if ("PARTIAL".equalsIgnoreCase(researchResp.status()) || !unresolvedFields.isEmpty()) {
            status = "PARTIAL";
            statusMessage = "Enrichment partially completed (" + unresolvedFields.size() + " unresolved fields)";
        } else {
            status = "COMPLETED";
            statusMessage = "Enrichment completed (" + finalAttributes.size() + " attributes, tier: " + assessment.priorityTier() + ")";
        }

        String profileJson = null;
        String assessmentJson = null;
        String recommendationJson = null;
        String findingsJson = null;
        try {
            if (profile != null) profileJson = objectMapper.writeValueAsString(profile);
            if (assessment != null) assessmentJson = objectMapper.writeValueAsString(assessment);
            if (recommendation != null) recommendationJson = objectMapper.writeValueAsString(recommendation);
            if (findings != null) findingsJson = objectMapper.writeValueAsString(findings);
        } catch (Exception ex) {
            log.warn("Failed serializing rich profile JSON for entity {}: {}", entityId, ex.getMessage());
        }

        try {
            JobState jobState = jobId != null ? activeJobs.get(jobId) : null;
            String rowUserId = (explicitUserId != null && !explicitUserId.isBlank())
                    ? explicitUserId
                    : (jobState != null ? jobState.userId : null);
            persistenceService.persistOrUpdate(new PersistEntityRequest(
                    entityId,
                    displayName,
                    entityType,
                    canonicalUrl,
                    entitySources,
                    finalAttributes,
                    status,
                    statusMessage,
                    assessment.priorityTier() != null ? assessment.priorityTier().name() : "NONE",
                    assessment.overallScore(),
                    profileJson,
                    assessmentJson,
                    recommendationJson,
                    findingsJson
            ), rowUserId);
        } catch (Exception ex) {
            log.warn("Failed persisting entity {} in database: {}", entityId, ex.getMessage());
        }

        long completedAtMs = System.currentTimeMillis();

        return new RowEnrichmentResult(
                rowId,
                rowIndex,
                rawRow,
                status,
                displayName,
                canonicalUrl,
                entityType,
                finalAttributes,
                unresolvedFields,
                conflicts,
                confidence,
                entitySources,
                null,
                status,
                workerId,
                statusMessage,
                startedAtMs,
                completedAtMs,
                profile,
                assessment,
                recommendation,
                findings
        );
    }

    private String extractMappedValue(Map<String, String> row, Map<String, String> mapping, String columnKey) {
        if (row == null || mapping == null) return null;
        String mappedCol = mapping.get(columnKey);
        if (mappedCol != null && row.containsKey(mappedCol)) {
            String val = row.get(mappedCol);
            if (val != null && !val.isBlank()) {
                val = val.trim();
                if ("urlColumn".equals(columnKey)) {
                    val = unwrapLink(val);
                }
                return (val != null && !val.isBlank()) ? val : null;
            }
        }
        return null;
    }

    private String unwrapLink(String url) {
        if (url == null || url.isBlank()) return url;
        String s = url.trim();
        if (s.startsWith("[") && s.contains("](") && s.endsWith(")")) {
            int openParen = s.indexOf("](");
            s = s.substring(openParen + 2, s.length() - 1).trim();
        } else if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        } else if (s.startsWith("<") && s.endsWith(">")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private EnrichmentJobResponse toJobResponse(JobState s) {
        List<RowEnrichmentResult> sortedResults = s.rowResultsMap.values().stream()
                .sorted(java.util.Comparator.comparingInt(RowEnrichmentResult::rowIndex))
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
                enrichmentTaskExecutor.getConcurrency(),
                s.degradedRows.get(),
                s.partialRows.get()
        );
    }

    public static String buildCompositeName(String firstName, String lastName, String fullName, String legacyName) {
        String cleanFull = sanitizeNameToken(fullName);
        if (cleanFull != null && !cleanFull.isBlank()) {
            return cleanFull;
        }

        String cleanFirst = sanitizeNameToken(firstName);
        String cleanLast = sanitizeNameToken(lastName);

        if (cleanFirst != null && !cleanFirst.isBlank() && cleanLast != null && !cleanLast.isBlank()) {
            return cleanFirst + " " + cleanLast;
        }
        if (cleanFirst != null && !cleanFirst.isBlank()) {
            return cleanFirst;
        }
        if (cleanLast != null && !cleanLast.isBlank()) {
            return cleanLast;
        }

        String cleanLegacy = sanitizeNameToken(legacyName);
        if (cleanLegacy != null && !cleanLegacy.isBlank()) {
            return cleanLegacy;
        }

        return null;
    }

    private static String sanitizeNameToken(String token) {
        if (token == null) return null;
        String s = token.trim();
        if (s.equalsIgnoreCase("null") || s.equalsIgnoreCase("undefined")) {
            return null;
        }
        s = s.replaceAll("\\s+", " ").trim();
        return s.isBlank() ? null : s;
    }

    private Instant parseInstantSafe(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
