package com.subdual.dataset_service.enrichment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.subdual.dataset_service.enrichment.api.dto.request.EnrichmentJobRequest;
import com.subdual.dataset_service.enrichment.api.dto.request.SingleEnrichmentRequest;
import com.subdual.dataset_service.enrichment.api.dto.response.EnrichmentJobResponse;
import com.subdual.dataset_service.enrichment.api.dto.response.RowEnrichmentResult;
import com.subdual.dataset_service.enrichment.integration.AiServiceClient;
import com.subdual.dataset_service.enrichment.integration.ResearchServiceClient;
import com.subdual.dataset_service.enrichment.model.JobState;
import com.subdual.dataset_service.enrichment.service.DatasetEnrichmentService;
import com.subdual.dataset_service.enrichment.service.helper.EnrichmentJobManager;
import com.subdual.dataset_service.enrichment.service.helper.EnrichmentTaskExecutor;
import com.subdual.dataset_service.enrichment.service.helper.RowEnrichmentProcessor;
import com.subdual.dataset_service.enrichment.service.helper.RowIdentityResolver;
import com.subdual.dataset_service.entity.service.EntityPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
public class DefaultDatasetEnrichmentService implements DatasetEnrichmentService {

    private final AiServiceClient aiServiceClient;
    private final ExecutorService enrichmentJobExecutor;
    private final EnrichmentTaskExecutor enrichmentTaskExecutor;
    private final EnrichmentJobManager jobManager;
    private final RowIdentityResolver rowIdentityResolver;
    private final RowEnrichmentProcessor rowEnrichmentProcessor;

    @Autowired
    public DefaultDatasetEnrichmentService(
            AiServiceClient aiServiceClient,
            ExecutorService enrichmentJobExecutor,
            EnrichmentTaskExecutor enrichmentTaskExecutor,
            EnrichmentJobManager jobManager,
            RowIdentityResolver rowIdentityResolver,
            RowEnrichmentProcessor rowEnrichmentProcessor
    ) {
        this.aiServiceClient = aiServiceClient;
        this.enrichmentJobExecutor = enrichmentJobExecutor;
        this.enrichmentTaskExecutor = enrichmentTaskExecutor;
        this.jobManager = jobManager;
        this.rowIdentityResolver = rowIdentityResolver;
        this.rowEnrichmentProcessor = rowEnrichmentProcessor;
    }

    public DefaultDatasetEnrichmentService(
            ResearchServiceClient researchServiceClient,
            AiServiceClient aiServiceClient,
            EntityPersistenceService persistenceService,
            ExecutorService enrichmentJobExecutor,
            EnrichmentTaskExecutor enrichmentTaskExecutor
    ) {
        this(researchServiceClient, aiServiceClient, persistenceService, enrichmentJobExecutor, enrichmentTaskExecutor,
                new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    public DefaultDatasetEnrichmentService(
            ResearchServiceClient researchServiceClient,
            AiServiceClient aiServiceClient,
            EntityPersistenceService persistenceService,
            ExecutorService enrichmentJobExecutor,
            EnrichmentTaskExecutor enrichmentTaskExecutor,
            @Nullable ObjectMapper objectMapper
    ) {
        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper().registerModule(new JavaTimeModule());
        RowIdentityResolver resolver = new RowIdentityResolver();
        EnrichmentJobManager manager = new EnrichmentJobManager();
        RowEnrichmentProcessor processor = new RowEnrichmentProcessor(researchServiceClient, aiServiceClient, persistenceService, mapper, resolver, manager);
        this.aiServiceClient = aiServiceClient;
        this.enrichmentJobExecutor = enrichmentJobExecutor;
        this.enrichmentTaskExecutor = enrichmentTaskExecutor;
        this.jobManager = manager;
        this.rowIdentityResolver = resolver;
        this.rowEnrichmentProcessor = processor;
    }

    public static String buildCompositeName(String firstName, String lastName, String fullName, String legacyName) {
        return RowIdentityResolver.buildCompositeName(firstName, lastName, fullName, legacyName);
    }

    @Override
    public EnrichmentJobResponse createAndSubmitJob(EnrichmentJobRequest request) {
        return createAndSubmitJob(request, null);
    }

    @Override
    public EnrichmentJobResponse createAndSubmitJob(EnrichmentJobRequest request, String userId) {
        JobState state = jobManager.createJobState(request, userId, rowIdentityResolver);
        enrichmentJobExecutor.submit(() -> executeJobAsync(state, request));
        return jobManager.toJobResponse(state, enrichmentTaskExecutor.getConcurrency());
    }

    @Override
    public Optional<EnrichmentJobResponse> getJob(String jobId) {
        return getJob(jobId, null);
    }

    @Override
    public Optional<EnrichmentJobResponse> getJob(String jobId, String userId) {
        return jobManager.getJob(jobId, userId, enrichmentTaskExecutor.getConcurrency());
    }

    @Override
    public List<EnrichmentJobResponse> listJobs() {
        return listJobs(null);
    }

    @Override
    public List<EnrichmentJobResponse> listJobs(String userId) {
        return jobManager.listJobs(userId, enrichmentTaskExecutor.getConcurrency());
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

        return rowEnrichmentProcessor.processSingleRow(
                "single-" + System.currentTimeMillis(), 0, row, mapping, entityType, requirement,
                targetFields, null, "worker-sync", System.currentTimeMillis(), userId
        );
    }

    @Override
    public boolean cancelJob(String jobId) {
        return cancelJob(jobId, null);
    }

    @Override
    public boolean cancelJob(String jobId, String userId) {
        return jobManager.cancelJob(jobId, userId);
    }

    @Override
    public SseEmitter subscribeJobEvents(String jobId) {
        return subscribeJobEvents(jobId, null);
    }

    @Override
    public SseEmitter subscribeJobEvents(String jobId, String userId) {
        return jobManager.subscribeJobEvents(jobId, userId, enrichmentTaskExecutor.getConcurrency());
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
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < rows.size(); i++) {
                final int rowIndex = i;
                final Map<String, String> row = rows.get(i);
                final String rowId = state.jobId + "-row-" + rowIndex;

                CompletableFuture<Void> future = enrichmentTaskExecutor.submitTask(() -> {
                    if (state.cancelled) {
                        return null;
                    }
                    state.processingRows.incrementAndGet();

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

                    jobManager.emitExecutionEvent(
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
                        RowEnrichmentResult result = rowEnrichmentProcessor.processSingleRow(
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
                            jobManager.emitExecutionEvent(
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
                            jobManager.emitExecutionEvent(
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

                        jobManager.emitExecutionEvent(
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

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

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

            jobManager.emitJobCompleted(state.jobId, state.status, state.completedRows.get(), state.failedRows.get(), state.durationMs);

        } catch (Exception ex) {
            log.error("Fatal error during enrichment job {}: {}", state.jobId, ex.getMessage(), ex);
            state.status = "FAILED";
            state.errorMessage = ex.getMessage();
            state.completedAt = Instant.now();
            state.durationMs = System.currentTimeMillis() - startMs;
            jobManager.emitJobCompleted(state.jobId, "FAILED", state.completedRows.get(), state.failedRows.get(), state.durationMs);
        }
    }
}
