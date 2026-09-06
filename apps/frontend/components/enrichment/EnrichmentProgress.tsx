"use client";

import React from "react";
import { EnrichmentProgressState } from "@/types/common";

interface EnrichmentProgressProps {
  progress: EnrichmentProgressState;
  jobId?: string;
  durationMs?: number;
  onCancel?: () => void;
}

export function EnrichmentProgress({
  progress,
  jobId,
  durationMs,
  onCancel,
}: EnrichmentProgressProps) {
  const processed = progress.completed + progress.failed;
  const percent =
    progress.total > 0
      ? Math.min(100, Math.round((processed / progress.total) * 100))
      : progress.isFinished
      ? 100
      : 0;

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-5 shadow-xs space-y-4">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
            {!progress.isFinished ? (
              <>
                <span className="animate-spin inline-block w-4 h-4 border-2 border-blue-600 border-t-transparent rounded-full" />
                <span>Enrichment in progress...</span>
              </>
            ) : (
              <>
                <span className="text-emerald-500 font-bold">✓</span>
                <span>Enrichment Run Finished</span>
              </>
            )}
          </h4>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
            {progress.isFinished
              ? durationMs != null
                ? `Processed ${progress.total} records in ${(durationMs / 1000).toFixed(1)}s`
                : "Batch job execution completed"
              : `Processing ${processed} of ${progress.total} records`}
            {jobId && <span className="font-mono text-[10px] text-zinc-400 ml-2">({jobId.slice(0, 8)})</span>}
          </p>
        </div>

        {onCancel && !progress.isFinished && (
          <button
            type="button"
            onClick={onCancel}
            className="text-xs font-medium text-rose-600 hover:text-rose-700 dark:text-rose-400 px-3 py-1.5 rounded-lg bg-rose-50 dark:bg-rose-950/40 hover:bg-rose-100 transition-colors self-start sm:self-auto"
          >
            Cancel Run
          </button>
        )}
      </div>

      {/* Progress Bar */}
      <div className="space-y-1.5">
        <div className="flex justify-between text-xs font-mono text-zinc-600 dark:text-zinc-400">
          <span>{progress.statusText || (progress.isFinished ? "Completed" : "Running")}</span>
          <span>{percent}%</span>
        </div>
        <div className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-full h-2 overflow-hidden">
          <div
            className="bg-blue-600 dark:bg-blue-500 h-2 rounded-full transition-all duration-300 ease-out"
            style={{ width: `${percent}%` }}
          />
        </div>
      </div>

      {/* Counters Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
        <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-[10px] uppercase tracking-wider text-zinc-400">Total Rows</div>
          <div className="text-sm font-semibold text-zinc-800 dark:text-zinc-200 mt-0.5">
            {progress.total}
          </div>
        </div>

        <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-[10px] uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
            Completed
          </div>
          <div className="text-sm font-semibold text-emerald-600 dark:text-emerald-400 mt-0.5">
            {progress.completed}
          </div>
        </div>

        <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-[10px] uppercase tracking-wider text-rose-500">Failed</div>
          <div className="text-sm font-semibold text-rose-600 dark:text-rose-400 mt-0.5">
            {progress.failed}
          </div>
        </div>

        <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-[10px] uppercase tracking-wider text-zinc-400">Remaining</div>
          <div className="text-sm font-semibold text-zinc-600 dark:text-zinc-400 mt-0.5">
            {progress.remaining}
          </div>
        </div>
      </div>
    </div>
  );
}
