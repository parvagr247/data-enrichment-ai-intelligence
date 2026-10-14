"use client";

import { useState, useRef, useEffect, useCallback } from "react";
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

export type WorkflowStep = "UPLOAD" | "PREVIEW" | "MAPPING" | "CONFIG" | "RUNNING" | "RESULTS";

const INITIAL_EXECUTION_STATE: LiveExecutionState = {
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
};

/**
 * Facade hook encapsulating batch dataset enrichment workflow, file profiling,
 * background job submission, SSE live event streaming, and fallback polling.
 */
export function useEnrichmentWorkflow() {
  const [step, setStep] = useState<WorkflowStep>("UPLOAD");
  const [fileName, setFileName] = useState("");
  const [rawRows, setRawRows] = useState<RawRow[]>([]);
  const [profileReport, setProfileReport] = useState<DatasetProfileReport | null>(null);
  const [mapping, setMapping] = useState<ColumnMapping>({});
  const [datasetEntityType, setDatasetEntityType] = useState<EntityType>("PERSON");
  const [userRequirement, setUserRequirement] = useState("");
  const [isUploading, setIsUploading] = useState(false);
  const [workflowError, setWorkflowError] = useState<string | null>(null);

  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [jobDurationMs, setJobDurationMs] = useState<number | undefined>(undefined);
  const [records, setRecords] = useState<EnrichedRecord[]>([]);
  const [tableFilter, setTableFilter] = useState<"ALL" | RowEnrichmentStatus>("ALL");
  const [selectedRecordForModal, setSelectedRecordForModal] = useState<EnrichedRecord | null>(null);

  const [executionState, setExecutionState] = useState<LiveExecutionState>(INITIAL_EXECUTION_STATE);

  const sseUnsubscribeRef = useRef<(() => void) | null>(null);
  const pollingRef = useRef<NodeJS.Timeout | null>(null);

  // Cleanup SSE and polling on unmount
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

      const localParsed = await parseDatasetFile(file);
      setFileName(localParsed.filename);
      setRawRows(localParsed.rows);

      const report = await datasetService.uploadAndProfile(file);
      setProfileReport(report);

      const initialMapping: ColumnMapping = {
        nameColumn: report.recommendedMapping?.nameColumn,
        firstNameColumn: report.recommendedMapping?.firstNameColumn,
        lastNameColumn: report.recommendedMapping?.lastNameColumn,
        fullNameColumn: report.recommendedMapping?.fullNameColumn,
        urlColumn: report.recommendedMapping?.urlColumn,
        organizationColumn: report.recommendedMapping?.organizationColumn,
        roleColumn: report.recommendedMapping?.roleColumn,
        emailColumn: report.recommendedMapping?.emailColumn,
        locationColumn: report.recommendedMapping?.locationColumn,
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

      const rowList = Object.values(updatedRows);
      const completed = rowList.filter(
        (r) => r.status === "COMPLETED" || r.status === "PARTIAL"
      ).length;
      const failed = rowList.filter((r) => r.status === "FAILED").length;
      const total = prev.job.total;
      const remaining = Math.max(0, total - completed - failed);

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
    setExecutionState(INITIAL_EXECUTION_STATE);
  };

  return {
    step,
    setStep,
    fileName,
    rawRows,
    profileReport,
    mapping,
    setMapping,
    datasetEntityType,
    setDatasetEntityType,
    userRequirement,
    setUserRequirement,
    isUploading,
    workflowError,
    setWorkflowError,
    activeJobId,
    jobDurationMs,
    records,
    tableFilter,
    setTableFilter,
    selectedRecordForModal,
    setSelectedRecordForModal,
    executionState,
    handleFileSelected,
    handleLoadSample,
    handleStartEnrichment,
    handleCancelEnrichment,
    handleResetWorkflow,
  };
}
