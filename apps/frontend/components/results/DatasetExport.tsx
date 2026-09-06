"use client";

import React, { useState } from "react";
import { EnrichedRecord } from "@/types/dataset";
import { exportDataset } from "@/lib/exporter";

interface DatasetExportProps {
  records: EnrichedRecord[];
  baseFilename: string;
}

export function DatasetExport({ records, baseFilename }: DatasetExportProps) {
  const [exportError, setExportError] = useState<string | null>(null);
  const [downloadSuccess, setDownloadSuccess] = useState<string | null>(null);

  const handleExport = (format: "csv" | "xlsx") => {
    try {
      setExportError(null);
      setDownloadSuccess(null);
      exportDataset(records, baseFilename, format);
      setDownloadSuccess(`Successfully exported ${format.toUpperCase()} dataset.`);
      setTimeout(() => setDownloadSuccess(null), 4000);
    } catch (err: unknown) {
      setExportError(err instanceof Error ? err.message : "Failed to export dataset");
    }
  };

  const completedCount = records.filter(
    (r) => r.status === "COMPLETED" || r.status === "PARTIAL"
  ).length;

  return (
    <div className="bg-white dark:bg-zinc-900 p-5 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Export Enriched Dataset
          </h4>
          <p className="text-xs text-zinc-500 mt-0.5">
            Download combined original columns and verified <code className="text-zinc-700 dark:text-zinc-300 font-mono">Enriched_*</code> attributes with confidence and provenance URLs.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => handleExport("csv")}
            disabled={records.length === 0}
            className="px-4 py-2 rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 text-xs font-semibold hover:opacity-90 disabled:opacity-40 transition-opacity flex items-center gap-1.5"
          >
            <span>Download CSV</span>
          </button>
          <button
            type="button"
            onClick={() => handleExport("xlsx")}
            disabled={records.length === 0}
            className="px-4 py-2 rounded-lg border border-zinc-300 dark:border-zinc-700 text-zinc-700 dark:text-zinc-300 text-xs font-semibold hover:bg-zinc-50 dark:hover:bg-zinc-800 disabled:opacity-40 transition-colors flex items-center gap-1.5"
          >
            <span>Download XLSX</span>
          </button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-4 text-xs text-zinc-500 pt-2 border-t border-zinc-100 dark:border-zinc-800">
        <div>
          Total Records: <span className="font-semibold text-zinc-800 dark:text-zinc-200">{records.length}</span>
        </div>
        <div>
          Enriched (Completed/Partial):{" "}
          <span className="font-semibold text-emerald-600 dark:text-emerald-400">{completedCount}</span>
        </div>
        <div className="text-zinc-400">
          Source file: <span className="font-mono">{baseFilename}</span>
        </div>
      </div>

      {downloadSuccess && (
        <div className="text-xs text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-950/40 p-2.5 rounded-lg border border-emerald-200 dark:border-emerald-900">
          ✓ {downloadSuccess}
        </div>
      )}

      {exportError && (
        <div className="text-xs text-rose-700 dark:text-rose-300 bg-rose-50 dark:bg-rose-950/40 p-2.5 rounded-lg border border-rose-200 dark:border-rose-900">
          ✕ {exportError}
        </div>
      )}
    </div>
  );
}
