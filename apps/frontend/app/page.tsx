"use client";

import React, { useState, useRef, useEffect, useCallback } from "react";
import {
  EntityType,
  ColumnMapping,
  RawRow,
  DatasetProfileReport,
  EnrichedRecord,
  RowEnrichmentStatus,
} from "@/types/dataset";
import { EnrichmentProgressState } from "@/types/common";
import { datasetService } from "@/services/datasetService";
import { parseDatasetFile } from "@/lib/fileParser";

// Workflow & Child Components
import { DatasetUpload } from "@/components/upload/DatasetUpload";
import { DatasetPreview } from "@/components/dataset/DatasetPreview";
import { ColumnMappingView } from "@/components/dataset/ColumnMappingView";
import { EnrichmentConfig } from "@/components/enrichment/EnrichmentConfig";
import { EnrichmentProgress } from "@/components/enrichment/EnrichmentProgress";
import { QualitySummary } from "@/components/results/QualitySummary";
import { EnrichedDatasetTable } from "@/components/results/EnrichedDatasetTable";
import { EvidenceDetailModal } from "@/components/results/EvidenceDetailModal";
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
  const [progress, setProgress] = useState<EnrichmentProgressState>({
    total: 0,
    completed: 0,
    processing: 0,
    failed: 0,
    remaining: 0,
    isFinished: false,
  });
  const [tableFilter, setTableFilter] = useState<"ALL" | RowEnrichmentStatus>("ALL");
  const [selectedRecordForModal, setSelectedRecordForModal] = useState<EnrichedRecord | null>(null);

  const pollingRef = useRef<NodeJS.Timeout | null>(null);

  // Stop polling on unmount
  useEffect(() => {
    return () => {
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

  const pollJobStatus = useCallback(
    (jobId: string, totalRowsCount: number) => {
      if (pollingRef.current) clearInterval(pollingRef.current);

      pollingRef.current = setInterval(async () => {
        try {
          const job = await datasetService.getJobStatus(jobId);

          const completed = job.completedRows || 0;
          const failed = job.failedRows || 0;
          const remaining = Math.max(0, totalRowsCount - completed - failed);

          setProgress({
            total: totalRowsCount,
            completed,
            processing: remaining > 0 ? 1 : 0,
            failed,
            remaining,
            isFinished: job.status === "COMPLETED" || job.status === "FAILED",
            statusText: `Backend Status: ${job.status}`,
          });

          if (job.status === "COMPLETED" || job.status === "FAILED") {
            if (pollingRef.current) clearInterval(pollingRef.current);
            setJobDurationMs(job.durationMs ?? undefined);

            if (job.rowResults && job.rowResults.length > 0) {
              const mappedRecords: EnrichedRecord[] = job.rowResults.map((r, i) => ({
                id: r.rowId || `row-${i}`,
                rowIndex: r.rowIndex,
                originalData: r.originalData,
                status: r.status,
                displayName: r.displayName,
                canonicalUrl: r.canonicalUrl,
                entityType: r.entityType,
                attributes: r.attributes,
                unresolvedFields: r.unresolvedFields,
                conflicts: r.conflicts,
                confidence: r.confidence,
                sources: r.sources,
                errorMessage: r.errorMessage,
              }));
              setRecords(mappedRecords);
            }

            if (job.status === "FAILED" && job.errorMessage) {
              setWorkflowError(job.errorMessage);
            }

            setStep("RESULTS");
          }
        } catch (err: unknown) {
          if (pollingRef.current) clearInterval(pollingRef.current);
          setWorkflowError(err instanceof Error ? err.message : "Error polling batch job");
          setStep("RESULTS");
        }
      }, 1000);
    },
    []
  );

  const handleStartEnrichment = async () => {
    try {
      setWorkflowError(null);
      setStep("RUNNING");

      const totalRowsCount = rawRows.length;
      setProgress({
        total: totalRowsCount,
        completed: 0,
        processing: totalRowsCount > 0 ? 1 : 0,
        failed: 0,
        remaining: totalRowsCount,
        isFinished: false,
        statusText: "Submitting batch job to Dataset Service...",
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
      pollJobStatus(jobResponse.jobId, totalRowsCount);
    } catch (err: unknown) {
      setWorkflowError(err instanceof Error ? err.message : "Failed to start batch enrichment");
      setStep("CONFIG");
    }
  };

  const handleCancelEnrichment = () => {
    if (pollingRef.current) {
      clearInterval(pollingRef.current);
      pollingRef.current = null;
    }
    setProgress((prev) => ({ ...prev, processing: 0, isFinished: true, statusText: "Cancelled" }));
    setStep("RESULTS");
  };

  const handleResetWorkflow = () => {
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
    setProgress({
      total: 0,
      completed: 0,
      processing: 0,
      failed: 0,
      remaining: 0,
      isFinished: false,
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

            {/* Step 5: Running or Results */}
            {(step === "RUNNING" || step === "RESULTS") && (
              <div className="space-y-6">
                {/* Progress Bar & Counters */}
                <EnrichmentProgress
                  progress={progress}
                  jobId={activeJobId ?? undefined}
                  durationMs={jobDurationMs}
                  onCancel={step === "RUNNING" ? handleCancelEnrichment : undefined}
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
                  <EnrichedDatasetTable
                    records={records}
                    mapping={mapping}
                    filter={tableFilter}
                    onSelectRecord={(rec) => setSelectedRecordForModal(rec)}
                  />
                )}
              </div>
            )}

            {/* Evidence & Provenance Detail Modal */}
            {selectedRecordForModal && (
              <EvidenceDetailModal
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
