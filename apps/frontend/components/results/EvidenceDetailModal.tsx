"use client";

import React, { useEffect } from "react";
import { EnrichedRecord } from "@/types/dataset";

interface EvidenceDetailModalProps {
  record: EnrichedRecord | null;
  onClose: () => void;
}

export function EvidenceDetailModal({ record, onClose }: EvidenceDetailModalProps) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  if (!record) return null;

  // Unify attributes between RowEnrichmentResult and legacy ResearchResponse
  const attributes: Record<
    string,
    {
      value: string;
      sourceUrl?: string | null;
      evidenceSnippet?: string | null;
      confidence?: string;
      conflictDetected?: boolean;
      conflictDescription?: string;
      corroboratingSources?: string[];
    }
  > = {};

  if (record.attributes) {
    Object.entries(record.attributes).forEach(([k, v]) => {
      attributes[k] = {
        value: v.value,
        sourceUrl: v.sourceUrl,
        evidenceSnippet: v.evidenceSnippet,
        confidence: v.confidence,
      };
    });
  } else if (record.response?.result?.attributes) {
    Object.entries(record.response.result.attributes).forEach(([k, v]) => {
      attributes[k] = {
        value: v.value,
        sourceUrl: v.sourceUrl,
        evidenceSnippet: v.evidenceSnippet,
        confidence: v.confidence,
        conflictDetected: v.conflictDetected,
        corroboratingSources: v.corroboratingSources,
      };
    });
  }

  const sources = record.sources || record.response?.sources || [];
  const conflicts = record.conflicts || [];
  const warnings = record.response?.warnings || [];

  const renderConfidenceBadge = (confidence?: string) => {
    const tier = (confidence || "UNKNOWN").toUpperCase();
    if (tier === "HIGH") {
      return (
        <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
          HIGH
        </span>
      );
    }
    if (tier === "MEDIUM") {
      return (
        <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
          MEDIUM
        </span>
      );
    }
    return (
      <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
        LOW
      </span>
    );
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-950/60 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl w-full max-w-3xl max-h-[90vh] flex flex-col shadow-xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="p-5 border-b border-zinc-100 dark:border-zinc-800 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-bold text-zinc-900 dark:text-zinc-100">
                {record.displayName || `Record #${record.rowIndex + 1}`}
              </h3>
              <span
                className={`px-2 py-0.5 text-xs font-semibold rounded-full ${
                  record.status === "COMPLETED"
                    ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                    : record.status === "PARTIAL"
                    ? "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300"
                    : "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300"
                }`}
              >
                {record.status}
              </span>
            </div>
            {record.canonicalUrl && (
              <a
                href={record.canonicalUrl}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-blue-600 dark:text-blue-400 hover:underline break-all mt-0.5 block"
              >
                {record.canonicalUrl}
              </a>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 text-xl font-bold"
          >
            &times;
          </button>
        </div>

        {/* Modal Scrollable Content */}
        <div className="p-5 overflow-y-auto space-y-6 text-xs">
          {/* Diagnostic Warnings or Error Alert */}
          {record.errorMessage && (
            <div className="p-3.5 bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 rounded-lg text-rose-800 dark:text-rose-200">
              <span className="font-semibold">Error:</span> {record.errorMessage}
            </div>
          )}

          {/* Conflicts List */}
          {conflicts.length > 0 && (
            <div className="p-3.5 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded-lg text-amber-800 dark:text-amber-200 space-y-1">
              <span className="font-semibold">Conflicting Evidence Detected:</span>
              <ul className="list-disc list-inside space-y-0.5">
                {conflicts.map((c, i) => (
                  <li key={i}>{c}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Pipeline Warnings */}
          {warnings.length > 0 && (
            <div className="p-3.5 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded-lg text-amber-800 dark:text-amber-200 space-y-1">
              <span className="font-semibold">Diagnostics:</span>
              <ul className="list-disc list-inside space-y-0.5">
                {warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Grounded Attributes & Verbatim Evidence Section */}
          <div className="space-y-3">
            <h4 className="font-semibold text-sm text-zinc-900 dark:text-zinc-100 uppercase tracking-wider text-[11px]">
              Grounded Attributes &amp; Verbatim Evidence
            </h4>

            {Object.keys(attributes).length === 0 ? (
              <p className="text-zinc-400 py-3 text-center italic">
                No verified attributes extracted for this record.
              </p>
            ) : (
              <div className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {Object.entries(attributes).map(([fieldKey, tuple]) => (
                  <div key={fieldKey} className="py-3 space-y-1.5">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-zinc-800 dark:text-zinc-200 capitalize">
                        {fieldKey.replace(/_/g, " ")}
                      </span>
                      <div className="flex items-center gap-2">
                        {tuple.conflictDetected && (
                          <span className="px-2 py-0.5 text-[10px] font-semibold rounded bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
                            Conflict Flagged
                          </span>
                        )}
                        {tuple.corroboratingSources && tuple.corroboratingSources.length > 1 && (
                          <span className="px-2 py-0.5 text-[10px] font-semibold rounded bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
                            Corroborated ({tuple.corroboratingSources.length})
                          </span>
                        )}
                        {renderConfidenceBadge(tuple.confidence)}
                      </div>
                    </div>

                    <div className="p-2.5 rounded-lg bg-zinc-50 dark:bg-zinc-800/60 font-medium text-zinc-900 dark:text-zinc-100">
                      {tuple.value}
                    </div>

                    {tuple.evidenceSnippet && (
                      <div className="text-zinc-500 dark:text-zinc-400 italic bg-zinc-50/50 dark:bg-zinc-800/30 p-2 rounded border border-zinc-100 dark:border-zinc-800">
                        &ldquo;{tuple.evidenceSnippet}&rdquo;
                      </div>
                    )}

                    {tuple.sourceUrl && (
                      <div className="text-[11px] text-zinc-400 truncate">
                        Source:{" "}
                        <a
                          href={tuple.sourceUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-blue-500 hover:underline"
                        >
                          {tuple.sourceUrl}
                        </a>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Discovered Sources Section */}
          {sources.length > 0 && (
            <div className="space-y-3 pt-2 border-t border-zinc-100 dark:border-zinc-800">
              <h4 className="font-semibold text-sm text-zinc-900 dark:text-zinc-100 uppercase tracking-wider text-[11px]">
                Discovered Sources ({sources.length})
              </h4>
              <div className="space-y-2">
                {sources.map((src, i) => (
                  <div
                    key={i}
                    className="p-2.5 rounded-lg border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20 flex flex-col sm:flex-row sm:items-center justify-between gap-1.5"
                  >
                    <div className="min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="px-1.5 py-0.5 rounded bg-zinc-200 dark:bg-zinc-700 text-[10px] font-semibold">
                          {src.sourceType}
                        </span>
                        <span className="font-medium text-zinc-800 dark:text-zinc-200 truncate">
                          {src.title || src.url}
                        </span>
                      </div>
                      <a
                        href={src.url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-blue-500 hover:underline truncate block text-[11px] mt-0.5"
                      >
                        {src.url}
                      </a>
                    </div>
                    {src.relevance != null && (
                      <span className="text-[11px] font-mono text-zinc-400 shrink-0">
                        Relevance: {(src.relevance * 100).toFixed(0)}%
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Original Input Data */}
          <div className="space-y-2 pt-2 border-t border-zinc-100 dark:border-zinc-800">
            <h4 className="font-semibold text-zinc-700 dark:text-zinc-300 uppercase tracking-wider text-[11px]">
              Original Seed Record
            </h4>
            <div className="grid grid-cols-2 gap-2 font-mono text-[11px]">
              {Object.entries(record.originalData).map(([k, v]) => (
                <div key={k} className="p-2 rounded bg-zinc-50 dark:bg-zinc-800/30">
                  <span className="text-zinc-400 block text-[10px] uppercase">{k}</span>
                  <span className="text-zinc-800 dark:text-zinc-200">{v || "—"}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Modal Footer */}
        <div className="p-4 border-t border-zinc-100 dark:border-zinc-800 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 hover:opacity-90 transition-opacity"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
