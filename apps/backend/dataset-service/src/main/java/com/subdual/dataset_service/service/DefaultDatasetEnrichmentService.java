package com.subdual.dataset_service.service;

import com.subdual.dataset_service.dto.EntityAttributeDto;
import com.subdual.dataset_service.dto.EntitySourceDto;
import com.subdual.dataset_service.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.dto.PersistEntityRequest;
import com.subdual.dataset_service.dto.RowEnrichmentResult;
import com.subdual.dataset_service.dto.SingleEnrichmentRequest;
import com.subdual.dataset_service.integration.client.AiServiceClient;
import com.subdual.dataset_service.integration.client.ResearchServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDatasetEnrichmentService implements DatasetEnrichmentService {

    private final ResearchServiceClient researchServiceClient;
    private final AiServiceClient aiServiceClient;
    private final EntityPersistenceService persistenceService;
    private final ExecutorService enrichmentJobExecutor;
    private final com.subdual.dataset_service.service.executor.EnrichmentTaskExecutor enrichmentTaskExecutor;

    private final Map<String, JobState> activeJobs = new ConcurrentHashMap<>();

    private static class JobState {
        String jobId;
        String datasetName;
        String userRequirement;
        volatile String status;
        int totalRows;
        java.util.concurrent.atomic.AtomicInteger completedRows = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failedRows = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger partialRows = new java.util.concurrent.atomic.AtomicInteger(0);
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
        String jobId = UUID.randomUUID().toString();
        int totalRows = request.rows() != null ? request.rows().size() : 0;

        JobState state = new JobState();
        state.jobId = jobId;
        state.datasetName = request.datasetName() != null ? request.datasetName() : "dataset-" + jobId.substring(0, 8);
        state.userRequirement = request.userRequirement();
        state.status = "PROCESSING";
        state.totalRows = totalRows;
        state.progress = 0;
        state.createdAt = Instant.now();

        if (request.rows() != null) {
            for (int i = 0; i < request.rows().size(); i++) {
                Map<String, String> row = request.rows().get(i);
                String name = extractMappedValue(row, request.columnMapping(), "nameColumn");
                String url = extractMappedValue(row, request.columnMapping(), "urlColumn");
                String displayName = (name != null && !name.isBlank()) ? name : (url != null && !url.isBlank() ? url : "Row " + (i + 1));
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
        JobState state = activeJobs.get(jobId);
        return state != null ? Optional.of(toJobResponse(state)) : Optional.empty();
    }

    @Override
    public List<EnrichmentJobResponse> listJobs() {
        return activeJobs.values().stream()
                .sorted((a, b) -> b.createdAt.compareTo(a.createdAt))
                .map(this::toJobResponse)
                .toList();
    }

    @Override
    public RowEnrichmentResult enrichSingle(SingleEnrichmentRequest request) {
        Map<String, String> row = request.row();
        Map<String, String> mapping = request.columnMapping();
        String entityType = request.entityType();
        String requirement = request.userRequirement();

        AiServiceClient.RequirementCallResponse reqResponse = aiServiceClient.interpretRequirement(requirement, entityType, row);
        List<String> targetFields = reqResponse != null ? reqResponse.requestedFields() : List.of();

        return processSingleRow("single-" + System.currentTimeMillis(), 0, row, mapping, entityType, requirement, targetFields);
    }

    @Override
    public boolean cancelJob(String jobId) {
        JobState state = activeJobs.get(jobId);
        if (state != null && "PROCESSING".equalsIgnoreCase(state.status)) {
            state.cancelled = true;
            state.status = "CANCELLED";
            state.completedAt = Instant.now();
            log.info("Cancelled dataset enrichment job '{}'", jobId);
            return true;
        }
        return false;
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
                    RowEnrichmentResult current = state.rowResultsMap.get(rowIndex);
                    if (current != null) {
                        state.rowResultsMap.put(rowIndex, new RowEnrichmentResult(
                                current.rowId(),
                                rowIndex,
                                current.originalData(),
                                "PROCESSING",
                                current.displayName(),
                                current.canonicalUrl(),
                                current.entityType(),
                                current.attributes(),
                                current.unresolvedFields(),
                                current.conflicts(),
                                current.confidence(),
                                current.sources(),
                                null
                        ));
                    }

                    try {
                        RowEnrichmentResult result = processSingleRow(
                                rowId,
                                rowIndex,
                                row,
                                request.columnMapping(),
                                request.defaultEntityType(),
                                request.userRequirement(),
                                targetFields
                        );
                        state.rowResultsMap.put(rowIndex, result);

                        if ("FAILED".equalsIgnoreCase(result.status())) {
                            state.failedRows.incrementAndGet();
                        } else if ("PARTIAL".equalsIgnoreCase(result.status())) {
                            state.partialRows.incrementAndGet();
                            state.completedRows.incrementAndGet();
                        } else {
                            state.completedRows.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        log.error("Failed enriching row {} in job {}: {}", rowIndex, state.jobId, ex.getMessage());
                        state.failedRows.incrementAndGet();
                        state.rowResultsMap.put(rowIndex, new RowEnrichmentResult(
                                rowId,
                                rowIndex,
                                row,
                                "FAILED",
                                "Unknown",
                                "",
                                request.defaultEntityType(),
                                Map.of(),
                                targetFields,
                                List.of(),
                                0.0,
                                List.of(),
                                ex.getMessage()
                        ));
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

            if (state.cancelled) {
                state.status = "CANCELLED";
            } else {
                int failed = state.failedRows.get();
                state.status = (failed == state.totalRows && state.totalRows > 0) ? "FAILED" : "COMPLETED";
            }
            state.completedAt = Instant.now();
            state.durationMs = System.currentTimeMillis() - startMs;
            log.info("Completed enrichment job '{}' in {} ms (completed={}, partial={}, failed={})",
                    state.jobId, state.durationMs, state.completedRows.get(), state.partialRows.get(), state.failedRows.get());
        } catch (Exception ex) {
            log.error("Fatal error during enrichment job {}: {}", state.jobId, ex.getMessage(), ex);
            state.status = "FAILED";
            state.errorMessage = ex.getMessage();
            state.completedAt = Instant.now();
            state.durationMs = System.currentTimeMillis() - startMs;
        }
    }

    private RowEnrichmentResult processSingleRow(
            String rowId,
            int rowIndex,
            Map<String, String> rawRow,
            Map<String, String> mapping,
            String defaultEntityType,
            String requirement,
            List<String> targetFields
    ) {
        String name = extractMappedValue(rawRow, mapping, "nameColumn");
        String url = extractMappedValue(rawRow, mapping, "urlColumn");
        String org = extractMappedValue(rawRow, mapping, "organizationColumn");
        String role = extractMappedValue(rawRow, mapping, "roleColumn");

        String rawType = extractMappedValue(rawRow, mapping, "entityTypeColumn");
        String entityType = (rawType != null && !rawType.isBlank()) ? rawType.trim().toUpperCase(Locale.ROOT) : defaultEntityType;

        if ((name == null || name.isBlank()) && (url == null || url.isBlank())) {
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
                    "Row missing required name or url identifier"
            );
        }

        String displayName = name != null ? name : "Unknown";
        String canonicalUrl = url != null ? url : "";

        // 1. Dispatch to Research Service
        ResearchServiceClient.ResearchCallResponse researchResp = null;
        String researchError = null;
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (rawRow != null) {
                metadata.putAll(rawRow);
            }
            researchResp = researchServiceClient.executeResearch(new ResearchServiceClient.ResearchCallRequest(
                    url,
                    entityType,
                    name,
                    org,
                    role,
                    targetFields,
                    requirement,
                    metadata
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
                    "Research service error: " + (researchError != null ? researchError : "No response")
            );
        }

        if (researchResp.result() != null && researchResp.result().displayName() != null) {
            displayName = researchResp.result().displayName();
        }
        if (researchResp.result() != null && researchResp.result().canonicalUrl() != null) {
            canonicalUrl = researchResp.result().canonicalUrl();
        }

        // 2. Prepare facts & sources from research
        Map<String, AiServiceClient.FactEvidenceCallDto> evidenceMap = new LinkedHashMap<>();
        List<String> sourceUrls = new ArrayList<>();
        List<EntitySourceDto> entitySources = new ArrayList<>();

        if (researchResp != null && researchResp.sources() != null) {
            for (ResearchServiceClient.SourceItemDto s : researchResp.sources()) {
                sourceUrls.add(s.url());
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
            }
        }

        // 3. Dispatch to AI Service for evidence-grounded synthesis
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

        // 4. Assemble final attributes & persistence
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

        // 5. Persist canonical entity
        String entityId = (researchResp != null && researchResp.entityId() != null)
                ? researchResp.entityId()
                : UUID.randomUUID().toString();

        try {
            persistenceService.persistOrUpdate(new PersistEntityRequest(
                    entityId,
                    displayName,
                    entityType,
                    canonicalUrl,
                    entitySources,
                    finalAttributes
            ));
        } catch (Exception ex) {
            log.warn("Failed persisting entity {} in database: {}", entityId, ex.getMessage());
        }

        String status;
        if ("FAILED".equalsIgnoreCase(researchResp.status())) {
            status = "FAILED";
        } else if ("PARTIAL".equalsIgnoreCase(researchResp.status()) || !unresolvedFields.isEmpty()) {
            status = "PARTIAL";
        } else {
            status = "COMPLETED";
        }

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
                null
        );
    }

    private String extractMappedValue(Map<String, String> row, Map<String, String> mapping, String columnKey) {
        if (row == null || mapping == null) return null;
        String mappedCol = mapping.get(columnKey);
        if (mappedCol != null && row.containsKey(mappedCol)) {
            String val = row.get(mappedCol);
            return (val != null && !val.isBlank()) ? val.trim() : null;
        }
        return null;
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
                s.errorMessage
        );
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
