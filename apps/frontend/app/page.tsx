"use client";

import React, { useState, useRef, useEffect, useCallback } from "react";
import {
  EntityType,
  ColumnMapping,
  RawRow,
  DatasetProfileReport,
  EnrichedRecord,
  RowEnrichmentStatus,
  LiveExecutionState,
  ExecutionEvent,
} from "@/types/dataset";
import { datasetService } from "@/services/datasetService";
import { parseDatasetFile } from "@/lib/fileParser";

// Workflow & Child Components
import { DatasetUpload } from "@/components/upload/DatasetUpload";
import { DatasetPreview } from "@/components/dataset/DatasetPreview";
import { ColumnMappingView } from "@/components/dataset/ColumnMappingView";
import { EnrichmentConfig } from "@/components/enrichment/EnrichmentConfig";
import { LiveExecutionDashboard } from "@/components/enrichment/LiveExecutionDashboard";
import { QualitySummary } from "@/components/results/QualitySummary";
import { EnrichedDatasetTable } from "@/components/results/EnrichedDatasetTable";
import { EvidenceDetailModal } from "@/components/results/EvidenceDetailModal";
import { ResearchProfileModal } from "@/components/results/ResearchProfileModal";
import { DatasetExport } from "@/components/results/DatasetExport";
import { SingleEntityResearch } from "@/components/single/SingleEntityResearch";
import { EntityCatalog } from "@/components/catalog/EntityCatalog";

type WorkflowStep = "UPLOAD" | "PREVIEW" | "MAPPING" | "CONFIG" | "RUNNING" | "RESULTS";

export default function Home() {
  const [activeTab, setActiveTab] = useState<"batch" | "single" | "catalog">("batch");

  // Workflow State
  const [step, setStep] = useState<WorkflowStep>("UPLOAD");
  const [fileName, setFileName] = useState("");
  const [rawRows, setRawRows] = useState<RawRow[]>([]);
  const [profileReport, setProfileReport] = useState<DatasetProfileReport | null>(null);
  const [mapping, setMapping] = useState<ColumnMapping>({});
  const [datasetEntityType, setDatasetEntityType] = useState<EntityType>("PERSON");
  const [userRequirement, setUserRequirement] = useState("");
  const [isUploading, setIsUploading] = useState(false);
  const [workflowError, setWorkflowError] = useState<string | null>(null);

  // Enrichment Job & Results State
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [jobDurationMs, setJobDurationMs] = useState<number | undefined>(undefined);
  const [records, setRecords] = useState<EnrichedRecord[]>([]);
  const [tableFilter, setTableFilter] = useState<"ALL" | RowEnrichmentStatus>("ALL");
  const [selectedRecordForModal, setSelectedRecordForModal] = useState<EnrichedRecord | null>(null);

  // Normalized Live Execution State
  const [executionState, setExecutionState] = useState<LiveExecutionState>({
    job: {
      id: "",
      status: "QUEUED",
      total: 0,
      completed: 0,
      failed: 0,
      remaining: 0,
      concurrency: 3,
    },
    rows: {},
    activeWorkers: {},
    activityLog: [],
  });

  const sseUnsubscribeRef = useRef<(() => void) | null>(null);
  const pollingRef = useRef<NodeJS.Timeout | null>(null);

  // Clean up subscriptions on unmount
  useEffect(() => {
    return () => {
      if (sseUnsubscribeRef.current) sseUnsubscribeRef.current();
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, []);

  const handleFileSelected = async (file: File) => {
    try {
      setIsUploading(true);
      setWorkflowError(null);

      // Parse file rows client-side for full in-memory dataset
      const localParsed = await parseDatasetFile(file);
      setFileName(localParsed.filename);
      setRawRows(localParsed.rows);

      // Send file to backend dataset-service (:9743) for official profiling
      const report = await datasetService.uploadAndProfile(file);
      setProfileReport(report);

      // Prepopulate column mapping from backend recommended mappings
      const initialMapping: ColumnMapping = {
        nameColumn: report.recommendedMapping?.nameColumn,
        urlColumn: report.recommendedMapping?.urlColumn,
        organizationColumn: report.recommendedMapping?.organizationColumn,
        roleColumn: report.recommendedMapping?.roleColumn,
      };
      setMapping(initialMapping);

      setStep("PREVIEW");
    } catch (err: unknown) {
      setWorkflowError(err instanceof Error ? err.message : "Failed to process dataset file");
    } finally {
      setIsUploading(false);
    }
  };

  const handleLoadSample = async () => {
    try {
      setIsUploading(true);
      setWorkflowError(null);
      const res = await fetch("/samples/sparse-people-sample.csv");
      if (!res.ok) throw new Error("Failed to load sample dataset");
      const blob = await res.blob();
      const file = new File([blob], "sparse-people-sample.csv", { type: "text/csv" });
      await handleFileSelected(file);
    } catch (err: unknown) {
      setWorkflowError(err instanceof Error ? err.message : "Failed to load sample dataset");
      setIsUploading(false);
    }
  };

  // Reconcile job status from backend response (used for handshake and fallback)
  const reconcileJobState = useCallback((job: any) => {
    const completed = job.completedRows || 0;
    const failed = job.failedRows || 0;
    const total = job.totalRows || 0;
    const remaining = Math.max(0, total - completed - failed);
    const concurrency = job.concurrency || 3;

    setExecutionState((prev) => {
      const updatedRows = { ...prev.rows };
      if (job.rowResults && Array.isArray(job.rowResults)) {
        for (const r of job.rowResults) {
          const existing = updatedRows[r.rowId];
          updatedRows[r.rowId] = {
            rowId: r.rowId,
            rowIndex: r.rowIndex,
            entity: r.displayName || existing?.entity || `Row ${r.rowIndex + 1}`,
            status: r.status,
            stage: r.stage || (r.status === "COMPLETED" ? "COMPLETED" : r.status === "FAILED" ? "FAILED" : "RESEARCH"),
            workerId: r.workerId || existing?.workerId,
            message: r.message || existing?.message,
            startedAt: r.startedAtMs || existing?.startedAt,
            completedAt: r.completedAtMs || existing?.completedAt,
            sourcesCount: r.sources ? r.sources.length : existing?.sourcesCount,
            attributesCount: r.attributes ? Object.keys(r.attributes).length : existing?.attributesCount,
            confidence: r.confidence ?? existing?.confidence,
            error: r.errorMessage || existing?.error,
            result: r.status === "COMPLETED" || r.status === "PARTIAL" ? {
              id: r.rowId,
              rowIndex: r.rowIndex,
              originalData: r.originalData || {},
              status: r.status,
              displayName: r.displayName,
              canonicalUrl: r.canonicalUrl,
              entityType: r.entityType,
              attributes: r.attributes || {},
              unresolvedFields: r.unresolvedFields || [],
              conflicts: r.conflicts || [],
              confidence: r.confidence,
              sources: r.sources || [],
              errorMessage: r.errorMessage,
            } : existing?.result,
          };
        }
      }

      return {
        ...prev,
        job: {
          id: job.jobId || prev.job.id,
          status: job.status || prev.job.status,
          total,
          completed,
          failed,
          remaining,
          concurrency,
          durationMs: job.durationMs,
          errorMessage: job.errorMessage,
        },
        rows: updatedRows,
      };
    });

    if (job.rowResults && Array.isArray(job.rowResults)) {
      const completedList: EnrichedRecord[] = job.rowResults
        .filter((r: any) => r.status === "COMPLETED" || r.status === "PARTIAL")
        .map((r: any) => ({
          id: r.rowId,
          rowIndex: r.rowIndex,
          originalData: r.originalData || {},
          status: r.status,
          displayName: r.displayName,
          canonicalUrl: r.canonicalUrl,
          entityType: r.entityType,
          attributes: r.attributes || {},
          unresolvedFields: r.unresolvedFields || [],
          conflicts: r.conflicts || [],
          confidence: r.confidence,
          sources: r.sources || [],
          errorMessage: r.errorMessage,
          profile: r.profile,
          assessment: r.assessment,
          recommendation: r.recommendation,
          findings: r.findings,
        }));
      if (completedList.length > 0) {
        setRecords(completedList);
      }
    }
  }, []);

  // Handle individual execution event from SSE stream
  const handleExecutionEvent = useCallback((event: ExecutionEvent) => {
    const timeFormatted = new Date(event.timestamp || Date.now()).toLocaleTimeString();

    setExecutionState((prev) => {
      const updatedRows = { ...prev.rows };
      const prevRow = updatedRows[event.rowId];

      const sourcesCount =
        event.metadata?.sourcesCount ??
        (event.metadata?.result?.sources ? event.metadata.result.sources.length : prevRow?.sourcesCount);
      const attributesCount =
        event.metadata?.attributesCount ??
        (event.metadata?.result?.attributes
          ? Object.keys(event.metadata.result.attributes).length
          : prevRow?.attributesCount);
      const confidence = event.metadata?.confidence ?? prevRow?.confidence;

      const mappedResult: EnrichedRecord | undefined =
        event.metadata?.result
          ? {
              id: event.rowId,
              rowIndex: event.rowIndex,
              originalData: event.metadata?.originalData || prevRow?.result?.originalData || {},
              status: event.status,
              displayName: event.entity,
              canonicalUrl: event.metadata.result.canonicalUrl,
              entityType: event.metadata.result.entityType,
              attributes: event.metadata.result.attributes || {},
              unresolvedFields: event.metadata.result.unresolvedFields || [],
              conflicts: event.metadata.result.conflicts || [],
              confidence: event.metadata.confidence ?? event.metadata.result.confidence,
              sources: event.metadata.result.sources || [],
              errorMessage: event.metadata?.error,
              profile: event.metadata.result.profile,
              assessment: event.metadata.result.assessment,
              recommendation: event.metadata.result.recommendation,
              findings: event.metadata.result.findings,
            }
          : prevRow?.result;

      updatedRows[event.rowId] = {
        rowId: event.rowId,
        rowIndex: event.rowIndex,
        entity: event.entity,
        status: event.status,
        stage: event.stage || prevRow?.stage || "RESEARCH",
        workerId: event.workerId || prevRow?.workerId,
        message: event.message || prevRow?.message,
        startedAt: prevRow?.startedAt || Date.now(),
        completedAt:
          event.status === "COMPLETED" || event.status === "FAILED" || event.status === "PARTIAL"
            ? Date.now()
            : prevRow?.completedAt,
        sourcesCount,
        attributesCount,
        confidence,
        error: event.metadata?.error || prevRow?.error,
        result: mappedResult,
      };

      // Update active workers state
      const updatedWorkers = { ...prev.activeWorkers };
      if (event.workerId) {
        if (event.status === "PROCESSING") {
          updatedWorkers[event.workerId] = {
            workerId: event.workerId,
            rowId: event.rowId,
            rowIndex: event.rowIndex,
            entity: event.entity,
            stage: event.stage,
            message: event.message,
            startedAt: prev.activeWorkers[event.workerId]?.startedAt || Date.now(),
            sourcesCount,
            attributesCount,
          };
        } else if (
          event.status === "COMPLETED" ||
          event.status === "FAILED" ||
          event.status === "CANCELLED" ||
          event.status === "PARTIAL"
        ) {
          delete updatedWorkers[event.workerId];
        }
      }

      // Calculate new completed / failed totals
      const rowList = Object.values(updatedRows);
      const completed = rowList.filter(
        (r) => r.status === "COMPLETED" || r.status === "PARTIAL"
      ).length;
      const failed = rowList.filter((r) => r.status === "FAILED").length;
      const total = prev.job.total;
      const remaining = Math.max(0, total - completed - failed);

      // Append to bounded activity log (latest 20 entries)
      const logType =
        event.status === "COMPLETED"
          ? "success"
          : event.status === "FAILED"
          ? "error"
          : event.stage === "AI_EXTRACTION"
          ? "warn"
          : "info";

      const newLogEntry = {
        id: `${event.jobId}-${event.rowId}-${Date.now()}-${Math.random()}`,
        timestamp: event.timestamp,
        timeFormatted,
        text: `Row #${event.rowIndex + 1} (${event.entity}) → ${event.message || event.stage || event.status}`,
        entity: event.entity,
        stage: event.stage,
        workerId: event.workerId,
        type: logType as "info" | "success" | "warn" | "error",
      };

      const updatedLog = [...prev.activityLog.slice(-19), newLogEntry];

      return {
        ...prev,
        job: {
          ...prev.job,
          completed,
          failed,
          remaining,
        },
        rows: updatedRows,
        activeWorkers: updatedWorkers,
        activityLog: updatedLog,
      };
    });

    // If row completed, stream record immediately into records state for live viewing
    if ((event.status === "COMPLETED" || event.status === "PARTIAL") && event.metadata?.result) {
      const res = event.metadata.result;
      const newRecord: EnrichedRecord = {
        id: event.rowId,
        rowIndex: event.rowIndex,
        originalData: event.metadata?.originalData || {},
        status: event.status,
        displayName: event.entity,
        canonicalUrl: res.canonicalUrl,
        entityType: res.entityType,
        attributes: res.attributes || {},
        unresolvedFields: res.unresolvedFields || [],
        conflicts: res.conflicts || [],
        confidence: event.metadata?.confidence ?? res.confidence,
        sources: res.sources || [],
        errorMessage: event.metadata?.error,
        profile: res.profile,
        assessment: res.assessment,
        recommendation: res.recommendation,
        findings: res.findings,
      };

      setRecords((prev) => {
        const idx = prev.findIndex((r) => r.id === event.rowId || r.rowIndex === event.rowIndex);
        if (idx >= 0) {
          const copy = [...prev];
          copy[idx] = newRecord;
          return copy;
        }
        return [...prev, newRecord].sort((a, b) => a.rowIndex - b.rowIndex);
      });
    }
  }, []);

  const handleStartEnrichment = async () => {
    try {
      setWorkflowError(null);
      setStep("RUNNING");
      setRecords([]);

      const totalRowsCount = rawRows.length;

      // Initialize normalized execution state with all rows in QUEUED state
      const initialRows: LiveExecutionState["rows"] = {};
      rawRows.forEach((r, idx) => {
        const name = mapping.nameColumn ? r[mapping.nameColumn] : null;
        const url = mapping.urlColumn ? r[mapping.urlColumn] : null;
        const displayName = name || url || `Row ${idx + 1}`;
        const rowId = `row-${idx}`;
        initialRows[rowId] = {
          rowId,
          rowIndex: idx,
          entity: displayName,
          status: "QUEUED",
          stage: "QUEUED",
          message: "Queued for worker pickup",
        };
      });

      setExecutionState({
        job: {
          id: "",
          status: "PROCESSING",
          total: totalRowsCount,
          completed: 0,
          failed: 0,
          remaining: totalRowsCount,
          concurrency: 3,
        },
        rows: initialRows,
        activeWorkers: {},
        activityLog: [
          {
            id: `init-${Date.now()}`,
            timestamp: new Date().toISOString(),
            timeFormatted: new Date().toLocaleTimeString(),
            text: `Enrichment job initialized for ${totalRowsCount} records. Connecting live stream...`,
            type: "info",
          },
        ],
      });

      // Submit asynchronous batch job to backend dataset-service (:9743)
      const jobResponse = await datasetService.submitBatchJob({
        datasetName: fileName || "uploaded-dataset",
        userRequirement: userRequirement.trim() || undefined,
        defaultEntityType: datasetEntityType,
        columnMapping: {
          ...(mapping.nameColumn && { nameColumn: mapping.nameColumn }),
          ...(mapping.urlColumn && { urlColumn: mapping.urlColumn }),
          ...(mapping.organizationColumn && { organizationColumn: mapping.organizationColumn }),
          ...(mapping.roleColumn && { roleColumn: mapping.roleColumn }),
        },
        rows: rawRows,
      });

      setActiveJobId(jobResponse.jobId);
      reconcileJobState(jobResponse);

      // Subscribe to Server-Sent Events for real-time observable execution
      if (sseUnsubscribeRef.current) sseUnsubscribeRef.current();

      let isStreamHealthy = false;
      const unsubscribe = datasetService.subscribeJobEvents(jobResponse.jobId, {
        onInit: (initData) => {
          isStreamHealthy = true;
          setExecutionState((prev) => ({
            ...prev,
            job: {
              ...prev.job,
              id: initData.jobId,
              concurrency: initData.concurrency || prev.job.concurrency,
              total: initData.totalRows || prev.job.total,
              status: initData.status || prev.job.status,
            },
          }));
        },
        onEvent: (ev) => {
          isStreamHealthy = true;
          handleExecutionEvent(ev);
        },
        onJobCompleted: (completedData) => {
          setJobDurationMs(completedData.durationMs);
          setExecutionState((prev) => ({
            ...prev,
            job: {
              ...prev.job,
              status: completedData.status,
              durationMs: completedData.durationMs,
              completed: completedData.completedRows,
              failed: completedData.failedRows,
              remaining: 0,
            },
            activeWorkers: {},
          }));
          setStep("RESULTS");
        },
        onError: () => {
          // If SSE connection encounters issues, start graceful fallback polling
          if (!isStreamHealthy && !pollingRef.current) {
            console.warn("SSE stream interrupted; engaging fallback status polling.");
            pollingRef.current = setInterval(async () => {
              try {
                const polled = await datasetService.getJobStatus(jobResponse.jobId);
                reconcileJobState(polled);
                if (
                  polled.status === "COMPLETED" ||
                  polled.status === "FAILED" ||
                  polled.status === "PARTIAL" ||
                  polled.status === "CANCELLED"
                ) {
                  if (pollingRef.current) clearInterval(pollingRef.current);
                  setJobDurationMs(polled.durationMs);
                  setStep("RESULTS");
                }
              } catch (err) {
                console.error("Fallback polling failed", err);
              }
            }, 1000);
          }
        },
      });

      sseUnsubscribeRef.current = unsubscribe;
    } catch (err: unknown) {
      setWorkflowError(err instanceof Error ? err.message : "Failed to start batch enrichment");
      setStep("CONFIG");
    }
  };

  const handleCancelEnrichment = async () => {
    if (sseUnsubscribeRef.current) {
      sseUnsubscribeRef.current();
      sseUnsubscribeRef.current = null;
    }
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
    if (activeJobId) {
      try {
        await datasetService.cancelJob(activeJobId);
      } catch (err) {
        console.warn("Failed to cancel job on server", err);
      }
    }
    setExecutionState((prev) => ({
      ...prev,
      job: {
        ...prev.job,
        status: "CANCELLED",
      },
      activeWorkers: {},
    }));
    setStep("RESULTS");
  };

  const handleResetWorkflow = () => {
    if (sseUnsubscribeRef.current) {
      sseUnsubscribeRef.current();
      sseUnsubscribeRef.current = null;
    }
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
    setStep("UPLOAD");
    setFileName("");
    setRawRows([]);
    setProfileReport(null);
    setMapping({});
    setUserRequirement("");
    setRecords([]);
    setActiveJobId(null);
    setSelectedRecordForModal(null);
    setWorkflowError(null);
    setExecutionState({
      job: {
        id: "",
        status: "QUEUED",
        total: 0,
        completed: 0,
        failed: 0,
        remaining: 0,
        concurrency: 3,
      },
      rows: {},
      activeWorkers: {},
      activityLog: [],
    });
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 p-4 md:p-8">
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Platform Header */}
        <header className="flex flex-col md:flex-row md:items-center md:justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800 gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Data Enrichment AI Intelligence Platform</h1>
            <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-0.5">
              Automated Entity Research, Multi-Source Fact Extraction &amp; Evidence-Grounded Dataset Enrichment
            </p>
          </div>
          <div className="flex items-center gap-2 text-xs font-mono">
            <span className="px-2.5 py-1 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
              Research :9741
            </span>
            <span className="px-2.5 py-1 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
              AI :9742
            </span>
            <span className="px-2.5 py-1 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
              Dataset :9743
            </span>
          </div>
        </header>

        {/* Tab Navigation */}
        <nav className="flex space-x-2 border-b border-zinc-200 dark:border-zinc-800 pb-2">
          <button
            type="button"
            onClick={() => setActiveTab("batch")}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors flex items-center gap-2 ${
              activeTab === "batch"
                ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                : "text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-900"
            }`}
          >
            <span>Batch Dataset Enrichment</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("single")}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
              activeTab === "single"
                ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                : "text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-900"
            }`}
          >
            Single Entity Seed
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("catalog")}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
              activeTab === "catalog"
                ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                : "text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-900"
            }`}
          >
            Persisted Entity Catalog
          </button>
        </nav>

        {/* TAB 1: BATCH DATASET ENRICHMENT */}
        {activeTab === "batch" && (
          <div className="space-y-6">
            {workflowError && (
              <div className="p-4 rounded-lg bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-900 text-rose-800 dark:text-rose-200 text-sm flex justify-between items-center">
                <span>{workflowError}</span>
                <button type="button" onClick={() => setWorkflowError(null)} className="font-bold ml-4">
                  &times;
                </button>
              </div>
            )}

            {/* Workflow Step Indicators */}
            <div className="flex items-center justify-between text-xs text-zinc-500 border-b border-zinc-200 dark:border-zinc-800 pb-3">
              <div className="flex items-center gap-3">
                <span className={`font-semibold ${step === "UPLOAD" ? "text-zinc-900 dark:text-zinc-100" : "text-zinc-400"}`}>
                  1. Upload
                </span>
                <span>&rarr;</span>
                <span className={`font-semibold ${step === "PREVIEW" ? "text-zinc-900 dark:text-zinc-100" : "text-zinc-400"}`}>
                  2. Preview &amp; Profile
                </span>
                <span>&rarr;</span>
                <span className={`font-semibold ${step === "MAPPING" ? "text-zinc-900 dark:text-zinc-100" : "text-zinc-400"}`}>
                  3. Column Understanding
                </span>
                <span>&rarr;</span>
                <span className={`font-semibold ${step === "CONFIG" ? "text-zinc-900 dark:text-zinc-100" : "text-zinc-400"}`}>
                  4. Strategy &amp; Objective
                </span>
                <span>&rarr;</span>
                <span className={`font-semibold ${step === "RUNNING" || step === "RESULTS" ? "text-zinc-900 dark:text-zinc-100" : "text-zinc-400"}`}>
                  5. Enrich &amp; Export
                </span>
              </div>

              {step !== "UPLOAD" && (
                <button
                  type="button"
                  onClick={handleResetWorkflow}
                  className="text-xs text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200 underline"
                >
                  Start Over with New File
                </button>
              )}
            </div>

            {/* Step 1: Upload */}
            {step === "UPLOAD" && (
              <DatasetUpload
                onFileSelected={handleFileSelected}
                onLoadSample={handleLoadSample}
                isLoading={isUploading}
              />
            )}

            {/* Step 2: Preview & Profile Report */}
            {step === "PREVIEW" && profileReport && (
              <DatasetPreview
                report={profileReport}
                onProceed={() => setStep("MAPPING")}
                onReset={handleResetWorkflow}
              />
            )}

            {/* Step 3: Column Mapping */}
            {step === "MAPPING" && profileReport && (
              <ColumnMappingView
                report={profileReport}
                mapping={mapping}
                onMappingChange={setMapping}
                onProceed={() => setStep("CONFIG")}
                onBack={() => setStep("PREVIEW")}
              />
            )}

            {/* Step 4: Enrichment Configuration */}
            {step === "CONFIG" && profileReport && (
              <EnrichmentConfig
                entityType={datasetEntityType}
                onEntityTypeChange={setDatasetEntityType}
                userRequirement={userRequirement}
                onUserRequirementChange={setUserRequirement}
                datasetHeaders={profileReport.columns.map((c) => c.columnName)}
                totalRows={rawRows.length}
                onStartEnrichment={handleStartEnrichment}
                onBack={() => setStep("MAPPING")}
              />
            )}

            {/* Step 5: Observable Live Execution Dashboard & Results */}
            {(step === "RUNNING" || step === "RESULTS") && (
              <div className="space-y-6">
                {/* Live Observability Dashboard */}
                <LiveExecutionDashboard
                  executionState={executionState}
                  isFinished={step === "RESULTS"}
                  onCancel={step === "RUNNING" ? handleCancelEnrichment : undefined}
                  onInspectEvidence={(rec) => setSelectedRecordForModal(rec)}
                  onViewResultsTable={() => {
                    const el = document.getElementById("results-dataset-table");
                    el?.scrollIntoView({ behavior: "smooth" });
                  }}
                />

                {/* Quality Summary & Filter (When completed/partial records exist) */}
                {records.length > 0 && (
                  <QualitySummary
                    records={records}
                    activeFilter={tableFilter}
                    onFilterChange={setTableFilter}
                  />
                )}

                {/* Export Card */}
                {records.some((r) => r.status === "COMPLETED" || r.status === "PARTIAL") && (
                  <DatasetExport records={records} baseFilename={fileName} />
                )}

                {/* Enriched Dataset Table */}
                {records.length > 0 && (
                  <div id="results-dataset-table">
                    <EnrichedDatasetTable
                      records={records}
                      mapping={mapping}
                      filter={tableFilter}
                      onSelectRecord={(rec) => setSelectedRecordForModal(rec)}
                    />
                  </div>
                )}
              </div>
            )}

            {/* Deep Research Profile & Evidence Modal */}
            {selectedRecordForModal && (
              <ResearchProfileModal
                record={selectedRecordForModal}
                onClose={() => setSelectedRecordForModal(null)}
              />
            )}
          </div>
        )}

        {/* TAB 2: SINGLE ENTITY SEED */}
        {activeTab === "single" && <SingleEntityResearch />}

        {/* TAB 3: PERSISTED ENTITY CATALOG */}
        {activeTab === "catalog" && <EntityCatalog />}
      </div>
    </div>
  );
}
