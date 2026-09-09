package com.subdual.dataset_service.enrichment.model;

import com.subdual.dataset_service.enrichment.api.dto.response.RowEnrichmentResult;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class JobState {
    public String jobId;
    public String userId;
    public String datasetName;
    public String userRequirement;
    public volatile String status;
    public int totalRows;
    public AtomicInteger completedRows = new AtomicInteger(0);
    public AtomicInteger failedRows = new AtomicInteger(0);
    public AtomicInteger partialRows = new AtomicInteger(0);
    public AtomicInteger degradedRows = new AtomicInteger(0);
    public AtomicInteger insufficientEvidenceRows = new AtomicInteger(0);
    public AtomicInteger processingRows = new AtomicInteger(0);
    public volatile int progress;
    public Instant createdAt;
    public Instant completedAt;
    public Long durationMs;
    public Map<Integer, RowEnrichmentResult> rowResultsMap = new ConcurrentHashMap<>();
    public String errorMessage;
    public volatile boolean cancelled = false;
}
