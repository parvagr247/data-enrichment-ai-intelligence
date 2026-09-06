"use client";

import React from "react";
import { EnrichedRecord, RowEnrichmentStatus } from "@/types/dataset";

interface QualitySummaryProps {
  records: EnrichedRecord[];
  activeFilter: "ALL" | RowEnrichmentStatus;
  onFilterChange: (filter: "ALL" | RowEnrichmentStatus) => void;
}

export function QualitySummary({
  records,
  activeFilter,
  onFilterChange,
}: QualitySummaryProps) {
  const total = records.length;
  const completed = records.filter((r) => r.status === "COMPLETED").length;
  const partial = records.filter((r) => r.status === "PARTIAL").length;
  const failed = records.filter((r) => r.status === "FAILED").length;

  // Gather unique enriched attribute keys
  const enrichedKeysSet = new Set<string>();
  let totalSources = 0;
  let totalConflicts = 0;

  for (const r of records) {
    if (r.attributes) {
      Object.keys(r.attributes).forEach((k) => enrichedKeysSet.add(k));
    }
    if (r.sources) {
      totalSources += r.sources.length;
    }
    if (r.conflicts && r.conflicts.length > 0) {
      totalConflicts += r.conflicts.length;
    }
  }

  const enrichedKeys = Array.from(enrichedKeysSet);

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-5 shadow-xs space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Enrichment Quality &amp; Attribute Summary
          </h4>
          <p className="text-xs text-zinc-500 mt-0.5">
            {completed + partial} of {total} records enriched with verified web evidence.
          </p>
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-1.5 self-start sm:self-auto text-xs">
          <button
            type="button"
            onClick={() => onFilterChange("ALL")}
            className={`px-3 py-1 rounded-md transition-colors font-medium ${
              activeFilter === "ALL"
                ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                : "bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200"
            }`}
          >
            All ({total})
          </button>
          <button
            type="button"
            onClick={() => onFilterChange("COMPLETED")}
            className={`px-3 py-1 rounded-md transition-colors font-medium ${
              activeFilter === "COMPLETED"
                ? "bg-emerald-600 text-white"
                : "bg-zinc-100 dark:bg-zinc-800 text-emerald-700 dark:text-emerald-400 hover:bg-zinc-200"
            }`}
          >
            Completed ({completed})
          </button>
          <button
            type="button"
            onClick={() => onFilterChange("PARTIAL")}
            className={`px-3 py-1 rounded-md transition-colors font-medium ${
              activeFilter === "PARTIAL"
                ? "bg-amber-600 text-white"
                : "bg-zinc-100 dark:bg-zinc-800 text-amber-700 dark:text-amber-400 hover:bg-zinc-200"
            }`}
          >
            Partial ({partial})
          </button>
          {failed > 0 && (
            <button
              type="button"
              onClick={() => onFilterChange("FAILED")}
              className={`px-3 py-1 rounded-md transition-colors font-medium ${
                activeFilter === "FAILED"
                  ? "bg-rose-600 text-white"
                  : "bg-zinc-100 dark:bg-zinc-800 text-rose-700 dark:text-rose-400 hover:bg-zinc-200"
              }`}
            >
              Failed ({failed})
            </button>
          )}
        </div>
      </div>

      {/* Metrics Strip */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-1 border-t border-zinc-100 dark:border-zinc-800">
        <div className="p-3 bg-zinc-50 dark:bg-zinc-800/30 rounded-lg">
          <div className="text-[10px] uppercase font-semibold text-zinc-400">Success Rate</div>
          <div className="text-lg font-bold text-zinc-800 dark:text-zinc-100">
            {total > 0 ? Math.round(((completed + partial) / total) * 100) : 0}%
          </div>
        </div>

        <div className="p-3 bg-zinc-50 dark:bg-zinc-800/30 rounded-lg">
          <div className="text-[10px] uppercase font-semibold text-zinc-400">Fields Enriched</div>
          <div className="text-lg font-bold text-zinc-800 dark:text-zinc-100">
            {enrichedKeys.length}
          </div>
        </div>

        <div className="p-3 bg-zinc-50 dark:bg-zinc-800/30 rounded-lg">
          <div className="text-[10px] uppercase font-semibold text-zinc-400">Sources Discovered</div>
          <div className="text-lg font-bold text-zinc-800 dark:text-zinc-100">
            {totalSources}
          </div>
        </div>

        <div className="p-3 bg-zinc-50 dark:bg-zinc-800/30 rounded-lg">
          <div className="text-[10px] uppercase font-semibold text-zinc-400">Conflicts Resolved</div>
          <div className="text-lg font-bold text-zinc-800 dark:text-zinc-100">
            {totalConflicts}
          </div>
        </div>
      </div>

      {/* Discovered Field Chips */}
      {enrichedKeys.length > 0 && (
        <div className="pt-1 flex flex-wrap items-center gap-1.5 text-xs">
          <span className="text-zinc-400 text-[11px] font-medium mr-1">Enriched Attributes:</span>
          {enrichedKeys.map((key) => (
            <span
              key={key}
              className="px-2 py-0.5 rounded-full bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300 font-mono text-[11px]"
            >
              Enriched_{key}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
