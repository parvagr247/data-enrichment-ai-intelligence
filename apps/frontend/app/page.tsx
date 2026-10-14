"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useAuth } from "@/context/AuthContext";
import { useEnrichmentWorkflow } from "@/hooks/useEnrichmentWorkflow";

// Workflow & Child Components
import { DatasetUpload } from "@/components/upload/DatasetUpload";
import { DatasetPreview } from "@/components/dataset/DatasetPreview";
import { ColumnMappingView } from "@/components/dataset/ColumnMappingView";
import { EnrichmentConfig } from "@/components/enrichment/EnrichmentConfig";
import { LiveExecutionDashboard } from "@/components/enrichment/LiveExecutionDashboard";
import { QualitySummary } from "@/components/results/QualitySummary";
import { EnrichedDatasetTable } from "@/components/results/EnrichedDatasetTable";
import { ResearchProfileModal } from "@/components/results/ResearchProfileModal";
import { DatasetExport } from "@/components/results/DatasetExport";
import { SingleEntityResearch } from "@/components/single/SingleEntityResearch";
import { EntityCatalog } from "@/components/catalog/EntityCatalog";
import { ProtectedRoute } from "@/components/auth/ProtectedRoute";

export default function Home() {
  const { user, logout } = useAuth();
  const [activeTab, setActiveTab] = useState<"batch" | "single" | "catalog">("batch");

  // Workflow Facade hook managing all enrichment states and SSE stream reconciliation
  const {
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
  } = useEnrichmentWorkflow();

  const userInitials = user?.name
    ? user.name.trim().split(' ').length >= 2
      ? (user.name.trim().split(' ')[0][0] + user.name.trim().split(' ')[1][0]).toUpperCase()
      : user.name.slice(0, 2).toUpperCase()
    : 'U';

  return (
    <ProtectedRoute>
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
          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 text-xs font-mono">
              <span className="px-2 py-0.5 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                Auth :9739
              </span>
              <span className="px-2 py-0.5 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                Research :9741
              </span>
              <span className="px-2 py-0.5 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                AI :9742
              </span>
              <span className="px-2 py-0.5 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                Dataset :9743
              </span>
            </div>

            {/* User Profile Badge & Logout */}
            <div className="flex items-center gap-2.5 pl-3 border-l border-zinc-300 dark:border-zinc-700">
              <Link
                href="/account"
                className="flex items-center gap-2 p-1 rounded-lg hover:bg-zinc-100 dark:hover:bg-zinc-850 transition-colors group"
                title="Account Settings & Data Ownership"
              >
                <div className="w-7 h-7 rounded-full bg-indigo-600 text-white flex items-center justify-center font-bold text-xs shadow-sm">
                  {userInitials}
                </div>
                <div className="text-left hidden sm:block">
                  <div className="text-xs font-medium text-zinc-900 dark:text-zinc-100 group-hover:text-indigo-600 dark:group-hover:text-indigo-400">
                    {user?.name || 'User'}
                  </div>
                  <div className="text-[10px] text-zinc-500 dark:text-zinc-400 max-w-[120px] truncate">
                    {user?.email || ''}
                  </div>
                </div>
              </Link>
              <button
                type="button"
                onClick={logout}
                className="p-1.5 text-zinc-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-950/30 rounded-lg transition-colors"
                title="Sign Out"
              >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                </svg>
              </button>
            </div>
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
    </ProtectedRoute>
  );
}
