"use client";

import React from "react";
import { EnrichmentProgressState } from "@/lib/api";

interface EnrichmentProgressProps {
  progress: EnrichmentProgressState;
  onCancel?: () => void;
}

export function EnrichmentProgress({
  progress,
  onCancel,
}: EnrichmentProgressProps) {
  const percent =
    progress.total > 0
      ? Math.round(
          ((progress.completed + progress.failed) / progress.total) * 100
        )
      : 0;

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-5 shadow-sm space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
            {!progress.isFinished ? (
              <>
                <svg
                  className="animate-spin h-4 w-4 text-blue-600"
                  fill="none"
                  viewBox="0 0 24 24"
                >
                  <circle
                    className="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    strokeWidth="4"
                  />
                  <path
                    className="opacity-75"
                    fill="currentColor"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
                  />
                </svg>
                Enriching Dataset Records...
              </>
            ) : (
              <>
                <span className="text-emerald-500 font-bold">✓</span>
                Enrichment Run Finished
              </>
            )}
          </h4>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
            Querying the Research Engine, verifying sources, and extracting evidence
          </p>
        </div>

        {onCancel && !progress.isFinished && (
          <button
            type="button"
            onClick={onCancel}
            className="text-xs font-medium text-red-600 hover:text-red-700 dark:text-red-400 px-2.5 py-1 rounded bg-red-50 dark:bg-red-950/40 hover:bg-red-100"
          >
            Cancel Run
          </button>
        )}
      </div>

      {/* Progress Bar */}
      <div className="space-y-1.5">
        <div className="flex justify-between text-xs font-mono text-zinc-600 dark:text-zinc-400">
          <span>Progress</span>
          <span>{percent}%</span>
        </div>
        <div className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-full h-2.5 overflow-hidden">
          <div
            className="bg-blue-600 h-2.5 rounded-full transition-all duration-300 ease-out"
            style={{ width: `${percent}%` }}
          />
        </div>
      </div>

      {/* Counters Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 pt-2">
        <div className="bg-zinc-50 dark:bg-zinc-800/50 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-xs text-zinc-400">Total</div>
          <div className="text-base font-semibold text-zinc-800 dark:text-zinc-200">
            {progress.total}
          </div>
        </div>

        <div className="bg-zinc-50 dark:bg-zinc-800/50 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-xs text-blue-500">Processing</div>
          <div className="text-base font-semibold text-blue-600 dark:text-blue-400">
            {progress.processing}
          </div>
        </div>

        <div className="bg-zinc-50 dark:bg-zinc-800/50 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-xs text-emerald-500">Completed</div>
          <div className="text-base font-semibold text-emerald-600 dark:text-emerald-400">
            {progress.completed}
          </div>
        </div>

        <div className="bg-zinc-50 dark:bg-zinc-800/50 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
          <div className="text-xs text-red-500">Failed</div>
          <div className="text-base font-semibold text-red-600 dark:text-red-400">
            {progress.failed}
          </div>
        </div>

        <div className="bg-zinc-50 dark:bg-zinc-800/50 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800 col-span-2 sm:col-span-1">
          <div className="text-xs text-zinc-400">Remaining</div>
          <div className="text-base font-semibold text-zinc-600 dark:text-zinc-400">
            {progress.remaining}
          </div>
        </div>
      </div>
    </div>
  );
}
