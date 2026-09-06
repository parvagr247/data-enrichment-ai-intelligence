"use client";

import React, { useEffect, useState } from "react";
import { LiveExecutionState, EnrichedRecord } from "@/types/dataset";

interface LiveExecutionDashboardProps {
  executionState: LiveExecutionState;
  onCancel?: () => void;
  onInspectEvidence?: (record: EnrichedRecord) => void;
  onViewResultsTable?: () => void;
  isFinished?: boolean;
}

export function LiveExecutionDashboard({
  executionState,
  onCancel,
  onInspectEvidence,
  onViewResultsTable,
  isFinished = false,
}: LiveExecutionDashboardProps) {
  const { job, rows, activeWorkers, activityLog } = executionState;
  const [now, setNow] = useState(Date.now());

  // Tick for active worker live elapsed timers
  useEffect(() => {
    if (isFinished) return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [isFinished]);

  const processed = job.completed + job.failed;
  const percent =
    job.total > 0
      ? Math.min(100, Math.round((processed / job.total) * 100))
      : isFinished
      ? 100
      : 0;

  // Derive completed rows sorted by completion time (newest first)
  const completedRows = Object.values(rows)
    .filter((r) => r.status === "COMPLETED" || r.status === "PARTIAL")
    .sort((a, b) => (b.completedAt ?? 0) - (a.completedAt ?? 0));

  // Derive failed rows
  const failedRows = Object.values(rows)
    .filter((r) => r.status === "FAILED")
    .sort((a, b) => (b.completedAt ?? 0) - (a.completedAt ?? 0));

  // Generate dynamic worker slots based on backend-provided concurrency
  const concurrency = job.concurrency > 0 ? job.concurrency : 3;
  const workerEntries = Object.values(activeWorkers);
  const workerSlots: Array<{
    slotNumber: number;
    workerId: string;
    activeWorker?: (typeof workerEntries)[0];
  }> = [];

  for (let i = 1; i <= concurrency; i++) {
    const defaultWorkerId = `worker-${i}`;
    // Find active worker assigned to this slot or matching workerId
    const activeWorker = workerEntries.find(
      (w) =>
        w.workerId === defaultWorkerId ||
        w.workerId.endsWith(`-${i}`) ||
        w.workerId.includes(`worker-${i}`)
    ) || (workerEntries.length >= i ? workerEntries[i - 1] : undefined);

    workerSlots.push({
      slotNumber: i,
      workerId: defaultWorkerId,
      activeWorker,
    });
  }

  const renderStageBadge = (stage?: string) => {
    switch (stage?.toUpperCase()) {
      case "RESEARCHING_IDENTITY":
      case "DISCOVERING":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300 border border-indigo-200/60 dark:border-indigo-800/40">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
            DISCOVERING
          </span>
        );
      case "COLLECTING_SOURCES":
      case "DISCOVERING_SOURCES":
      case "RESEARCH":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border border-blue-200/60 dark:border-blue-800/40">
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
            COLLECTING SOURCES
          </span>
        );
      case "EXTRACTING_EVIDENCE":
      case "AI_EXTRACTION":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-cyan-50 text-cyan-700 dark:bg-cyan-950/60 dark:text-cyan-300 border border-cyan-200/60 dark:border-cyan-800/40">
            <span className="w-1.5 h-1.5 rounded-full bg-cyan-500 animate-pulse" />
            EXTRACTING EVIDENCE
          </span>
        );
      case "AI_ENRICHMENT":
      case "ANALYZING_ACTIVITY":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300 border border-purple-200/60 dark:border-purple-800/40">
            <span className="w-1.5 h-1.5 rounded-full bg-purple-500 animate-pulse" />
            AI ENRICHMENT
          </span>
        );
      case "ASSESSING":
      case "ASSESSING_OBJECTIVE":
      case "GENERATING_PROFILE":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-violet-50 text-violet-700 dark:bg-violet-950/60 dark:text-violet-300 border border-violet-200/60 dark:border-violet-800/40">
            <span className="w-1.5 h-1.5 rounded-full bg-violet-500 animate-pulse" />
            ASSESSING OBJECTIVE
          </span>
        );
      case "PERSISTING":
      case "PERSISTENCE":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-orange-50 text-orange-700 dark:bg-orange-950/60 dark:text-orange-300 border border-orange-200/60 dark:border-orange-800/40">
            <span className="w-1.5 h-1.5 rounded-full bg-orange-500" />
            PERSISTING
          </span>
        );
      case "COMPLETED":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300">
            ✓ COMPLETED
          </span>
        );
      case "AI_DEGRADED":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-amber-50 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300 border border-amber-300">
            ⚠ AI DEGRADED
          </span>
        );
      case "INSUFFICIENT_EVIDENCE":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border border-zinc-300">
            ∅ INSUFFICIENT EVIDENCE
          </span>
        );
      case "FAILED":
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-semibold bg-rose-50 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300">
            ✕ FAILED
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-medium bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
            {stage || "QUEUED"}
          </span>
        );
    }
  };

  return (
    <div className="space-y-6">
      {/* 1. OVERALL PROGRESS & CONCURRENCY HEADER */}
      <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-5 shadow-xs space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
                {!isFinished ? (
                  <>
                    <span className="relative flex h-3 w-3">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75" />
                      <span className="relative inline-flex rounded-full h-3 w-3 bg-blue-600" />
                    </span>
                    <span>Live Parallel Enrichment in Progress</span>
                  </>
                ) : job.status === "CANCELLED" ? (
                  <>
                    <span className="text-amber-500 font-bold text-lg">⏹</span>
                    <span>Enrichment Run Cancelled</span>
                  </>
                ) : (
                  <>
                    <span className="text-emerald-500 font-bold text-lg">✓</span>
                    <span>Batch Enrichment Finished</span>
                  </>
                )}
              </h3>
              {job.id && (
                <span className="font-mono text-[11px] text-zinc-400 bg-zinc-100 dark:bg-zinc-800/80 px-2 py-0.5 rounded">
                  Job {job.id.slice(0, 8)}
                </span>
              )}
            </div>
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              {isFinished
                ? `Processed ${job.total} records (${job.completed} completed, ${job.failed} failed) in ${
                    job.durationMs != null ? (job.durationMs / 1000).toFixed(1) : "0"
                  }s`
                : `Processing ${processed} of ${job.total} records across active workers`}
            </p>
          </div>

          <div className="flex items-center gap-2 self-start sm:self-auto">
            {/* Real concurrency badge from backend state */}
            <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-zinc-100 dark:bg-zinc-800 text-xs font-mono font-medium text-zinc-700 dark:text-zinc-300 border border-zinc-200 dark:border-zinc-700/60">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span>Concurrency: {concurrency} workers</span>
            </div>

            {onCancel && !isFinished && (
              <button
                type="button"
                onClick={onCancel}
                className="text-xs font-medium text-rose-600 hover:text-rose-700 dark:text-rose-400 px-3 py-1.5 rounded-lg bg-rose-50 dark:bg-rose-950/40 hover:bg-rose-100 transition-colors"
              >
                Cancel Run
              </button>
            )}

            {isFinished && onViewResultsTable && (
              <button
                type="button"
                onClick={onViewResultsTable}
                className="text-xs font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400 px-3 py-1.5 rounded-lg bg-blue-50 dark:bg-blue-950/40 hover:bg-blue-100 transition-colors"
              >
                View Results Table &rarr;
              </button>
            )}
          </div>
        </div>

        {/* Progress Bar */}
        <div className="space-y-1.5">
          <div className="flex justify-between text-xs font-mono text-zinc-600 dark:text-zinc-400">
            <span>
              {isFinished
                ? job.status === "CANCELLED"
                  ? "Execution Cancelled by User"
                  : "All Records Processed"
                : `${job.remaining} rows remaining in queue`}
            </span>
            <span>{percent}%</span>
          </div>
          <div className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-full h-2.5 overflow-hidden">
            <div
              className={`h-2.5 rounded-full transition-all duration-300 ease-out ${
                job.status === "CANCELLED"
                  ? "bg-amber-500"
                  : isFinished
                  ? "bg-emerald-500"
                  : "bg-blue-600 dark:bg-blue-500"
              }`}
              style={{ width: `${percent}%` }}
            />
          </div>
        </div>

        {/* Counters Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2 pt-1">
          <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
            <div className="text-[10px] uppercase tracking-wider text-zinc-400">Total Rows</div>
            <div className="text-sm font-semibold text-zinc-800 dark:text-zinc-200 mt-0.5">
              {job.total}
            </div>
          </div>

          <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
            <div className="text-[10px] uppercase tracking-wider text-blue-500">Processing</div>
            <div className="text-sm font-semibold text-blue-600 dark:text-blue-400 mt-0.5">
              {workerEntries.length}
            </div>
          </div>

          <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
            <div className="text-[10px] uppercase tracking-wider text-emerald-600 dark:text-emerald-400">
              Completed
            </div>
            <div className="text-sm font-semibold text-emerald-600 dark:text-emerald-400 mt-0.5">
              {job.completed}
            </div>
          </div>

          <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
            <div className="text-[10px] uppercase tracking-wider text-rose-500">Failed</div>
            <div className="text-sm font-semibold text-rose-600 dark:text-rose-400 mt-0.5">
              {job.failed}
            </div>
          </div>

          <div className="bg-zinc-50 dark:bg-zinc-800/40 p-2.5 rounded-lg text-center border border-zinc-100 dark:border-zinc-800">
            <div className="text-[10px] uppercase tracking-wider text-zinc-400">Remaining</div>
            <div className="text-sm font-semibold text-zinc-600 dark:text-zinc-400 mt-0.5">
              {job.remaining}
            </div>
          </div>
        </div>
      </div>

      {/* 2. DYNAMIC ACTIVE WORKERS VIEW */}
      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400 flex items-center gap-1.5">
            <span>Active Worker Slots</span>
            <span className="text-[10px] px-1.5 py-0.2 bg-zinc-200 dark:bg-zinc-800 rounded-full font-mono">
              {workerEntries.length} / {concurrency} busy
            </span>
          </h4>
          <span className="text-[11px] text-zinc-400">Dynamically driven by backend execution state</span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          {workerSlots.map((slot) => {
            const active = slot.activeWorker;
            const elapsedSeconds = active?.startedAt
              ? Math.max(0, Math.floor((now - active.startedAt) / 1000))
              : 0;

            if (active) {
              return (
                <div
                  key={slot.workerId}
                  className="bg-white dark:bg-zinc-900 border border-blue-200 dark:border-blue-900/60 rounded-xl p-4 shadow-xs space-y-3 relative overflow-hidden transition-all duration-200"
                >
                  <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-500 via-indigo-500 to-purple-500" />
                  
                  <div className="flex items-start justify-between gap-2">
                    <div className="space-y-0.5">
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-mono font-bold text-zinc-700 dark:text-zinc-300">
                          Worker {slot.slotNumber}
                        </span>
                        <span className="text-[11px] font-mono text-zinc-400">
                          Row #{(active.rowIndex ?? 0) + 1}
                        </span>
                      </div>
                      <div className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 truncate max-w-[200px]">
                        {active.entity || "Entity"}
                      </div>
                    </div>
                    {renderStageBadge(active.stage)}
                  </div>

                  <div className="bg-zinc-50 dark:bg-zinc-800/60 rounded-lg p-2.5 text-xs space-y-1.5">
                    <div className="text-zinc-600 dark:text-zinc-300 line-clamp-2">
                      {active.message || "Executing task..."}
                    </div>

                    <div className="flex items-center justify-between text-[11px] text-zinc-400 pt-0.5 font-mono">
                      <span>
                        {active.sourcesCount != null && active.sourcesCount > 0 ? (
                          <span className="text-blue-600 dark:text-blue-400 font-medium">
                            ● {active.sourcesCount} sources discovered
                          </span>
                        ) : (
                          <span>Scanning public sources</span>
                        )}
                      </span>
                      <span>{elapsedSeconds}s elapsed</span>
                    </div>
                  </div>
                </div>
              );
            }

            // Idle slot card
            return (
              <div
                key={slot.workerId}
                className="bg-zinc-50/70 dark:bg-zinc-900/40 border border-dashed border-zinc-200 dark:border-zinc-800 rounded-xl p-4 flex flex-col justify-between space-y-3 opacity-60"
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-mono font-semibold text-zinc-400">
                    Worker {slot.slotNumber}
                  </span>
                  <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-400">
                    {isFinished ? "FINISHED" : "IDLE"}
                  </span>
                </div>
                <div className="text-xs text-zinc-400 italic">
                  {isFinished ? "Completed all assigned rows" : "Waiting for next available queue item..."}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 3. TWO-COLUMN SPLIT: RECENTLY COMPLETED vs FAILED & LIVE ACTIVITY LOG */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Recently Completed Records (Live Streamed) */}
        <div className="lg:col-span-7 space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400 flex items-center gap-1.5">
              <span>Recently Completed Rows</span>
              <span className="text-[10px] px-1.5 py-0.2 bg-emerald-100 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 rounded-full font-mono">
                {completedRows.length} ready
              </span>
            </h4>
            <span className="text-[11px] text-zinc-400">Inspect evidence immediately</span>
          </div>

          {completedRows.length === 0 ? (
            <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-8 text-center text-xs text-zinc-400 space-y-1">
              <div className="text-lg">⏳</div>
              <div>Waiting for first row to complete...</div>
              <p className="text-[11px] text-zinc-500">
                Enriched rows appear here in real time as workers complete research.
              </p>
            </div>
          ) : (
            <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl divide-y divide-zinc-100 dark:divide-zinc-800 overflow-hidden shadow-xs max-h-[380px] overflow-y-auto">
              {completedRows.map((r) => {
                const recordForModal: EnrichedRecord = r.result || {
                  id: r.rowId,
                  rowIndex: r.rowIndex,
                  originalData: {},
                  status: r.status,
                  displayName: r.entity,
                  attributes: {},
                };

                return (
                  <div
                    key={r.rowId}
                    className="p-3.5 flex items-center justify-between gap-3 hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30 transition-colors"
                  >
                    <div className="space-y-0.5 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-emerald-500 font-bold text-xs">✓</span>
                        <span className="text-xs font-mono text-zinc-400">Row #{r.rowIndex + 1}</span>
                        <span className="text-xs font-semibold text-zinc-900 dark:text-zinc-100 truncate">
                          {r.entity}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 text-[11px] text-zinc-500">
                        <span>{r.attributesCount ?? 0} attributes</span>
                        <span>&bull;</span>
                        <span>{r.sourcesCount ?? 0} sources</span>
                        {r.confidence != null && (
                          <>
                            <span>&bull;</span>
                            <span className="font-mono">
                              {(r.confidence * 100).toFixed(0)}% conf
                            </span>
                          </>
                        )}
                      </div>
                    </div>

                    {onInspectEvidence && (
                      <button
                        type="button"
                        onClick={() => onInspectEvidence(recordForModal)}
                        className="text-xs font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400 px-2.5 py-1 rounded bg-blue-50 dark:bg-blue-950/40 hover:bg-blue-100 transition-colors shrink-0"
                      >
                        Inspect Evidence
                      </button>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Right Column: Failed Rows & Live Activity Log */}
        <div className="lg:col-span-5 space-y-4">
          {/* Failed Rows Panel (if any failures occur) */}
          {failedRows.length > 0 && (
            <div className="space-y-2">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-rose-600 dark:text-rose-400 flex items-center gap-1.5">
                <span>Failed Rows ({failedRows.length})</span>
                <span className="text-[10px] text-zinc-400 font-normal">
                  (Isolated &bull; Batch continues)
                </span>
              </h4>
              <div className="bg-rose-50/60 dark:bg-rose-950/30 border border-rose-200 dark:border-rose-900/50 rounded-xl p-3 divide-y divide-rose-100 dark:divide-rose-900/40 max-h-[160px] overflow-y-auto">
                {failedRows.map((f) => (
                  <div key={f.rowId} className="py-2 first:pt-0 last:pb-0 space-y-0.5">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-semibold text-rose-900 dark:text-rose-200">
                        Row #{f.rowIndex + 1} &bull; {f.entity}
                      </span>
                      <span className="text-[10px] font-mono px-1.5 py-0.2 rounded bg-rose-200/60 dark:bg-rose-900/60 text-rose-800 dark:text-rose-200">
                        FAILED
                      </span>
                    </div>
                    <p className="text-[11px] text-rose-700 dark:text-rose-400 truncate">
                      {f.error || f.message || "Row processing encountered an upstream error"}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Live Activity Log */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                Live Activity Log
              </h4>
              <span className="text-[10px] font-mono text-zinc-400">Bounded to latest 20</span>
            </div>

            <div className="bg-zinc-900 dark:bg-black text-zinc-300 font-mono text-[11px] p-3 rounded-xl border border-zinc-800 max-h-[260px] overflow-y-auto space-y-1.5 shadow-inner">
              {activityLog.length === 0 ? (
                <div className="text-zinc-500 italic py-4 text-center">
                  Connecting to live execution event stream...
                </div>
              ) : (
                activityLog.map((log) => (
                  <div key={log.id} className="leading-tight flex items-start gap-2">
                    <span className="text-zinc-500 shrink-0">{log.timeFormatted}</span>
                    <span
                      className={`shrink-0 ${
                        log.type === "success"
                          ? "text-emerald-400"
                          : log.type === "error"
                          ? "text-rose-400"
                          : log.type === "warn"
                          ? "text-amber-400"
                          : "text-blue-400"
                      }`}
                    >
                      {log.type === "success" ? "✓" : log.type === "error" ? "✕" : "→"}
                    </span>
                    <span className="text-zinc-200 break-words">{log.text}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
