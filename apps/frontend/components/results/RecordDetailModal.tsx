'use client';

import React, { useEffect } from 'react';
import { EnrichedRecord, ConfidenceTier } from '@/lib/api';

interface RecordDetailModalProps {
  record: EnrichedRecord | null;
  onClose: () => void;
}

export const RecordDetailModal: React.FC<RecordDetailModalProps> = ({ record, onClose }) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  if (!record) return null;

  const result = record.response?.result;
  const attributes = result?.attributes || {};
  const sources = record.response?.sources || [];
  const warnings = record.response?.warnings || [];

  const renderConfidenceBadge = (confidence?: ConfidenceTier | string) => {
    const tier = (confidence || 'UNKNOWN').toUpperCase();
    if (tier === 'HIGH') {
      return (
        <span className="px-2 py-0.5 text-xs font-semibold rounded bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
          HIGH
        </span>
      );
    }
    if (tier === 'MEDIUM') {
      return (
        <span className="px-2 py-0.5 text-xs font-semibold rounded bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
          MEDIUM
        </span>
      );
    }
    if (tier === 'LOW') {
      return (
        <span className="px-2 py-0.5 text-xs font-semibold rounded bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
          LOW
        </span>
      );
    }
    return (
      <span className="px-2 py-0.5 text-xs font-semibold rounded bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
        UNKNOWN
      </span>
    );
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl max-w-3xl w-full max-h-[90vh] flex flex-col shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-5 border-b border-zinc-200 dark:border-zinc-800 flex items-start justify-between bg-zinc-50 dark:bg-zinc-800/30">
          <div>
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-bold text-zinc-900 dark:text-zinc-100">
                {result?.displayName || record.originalData.name || `Row #${record.rowIndex + 1}`}
              </h3>
              {result?.entityType && (
                <span className="px-2 py-0.5 text-xs font-semibold rounded bg-zinc-200 dark:bg-zinc-700 text-zinc-800 dark:text-zinc-200">
                  {result.entityType}
                </span>
              )}
              <span
                className={`px-2 py-0.5 text-xs font-semibold rounded ${
                  record.status === 'COMPLETED'
                    ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300'
                    : record.status === 'PARTIAL'
                    ? 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300'
                    : record.status === 'FAILED'
                    ? 'bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300'
                    : 'bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300'
                }`}
              >
                {record.status}
              </span>
            </div>
            {result?.canonicalUrl && (
              <a
                href={result.canonicalUrl}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-blue-600 dark:text-blue-400 hover:underline break-all mt-1 inline-block"
              >
                {result.canonicalUrl}
              </a>
            )}
            {record.response?.executionTimeMs && (
              <span className="text-[11px] text-zinc-400 ml-3">
                Latency: {record.response.executionTimeMs}ms
              </span>
            )}
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200 text-xl font-semibold px-2 py-1"
            aria-label="Close modal"
          >
            &times;
          </button>
        </div>

        {/* Scrollable Content */}
        <div className="p-5 overflow-y-auto space-y-6">
          {/* Error Notice */}
          {record.errorMessage && (
            <div className="p-3.5 bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 rounded-lg text-xs text-rose-800 dark:text-rose-200">
              <div className="font-semibold mb-0.5">Enrichment Error:</div>
              <div>{record.errorMessage}</div>
            </div>
          )}

          {/* Warnings Banner */}
          {warnings.length > 0 && (
            <div className="p-3 bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900 rounded-lg text-xs text-amber-800 dark:text-amber-200 space-y-1">
              <div className="font-semibold">Diagnostics / Warnings:</div>
              <ul className="list-disc list-inside space-y-0.5">
                {warnings.map((w, idx) => (
                  <li key={idx}>{w}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Extracted Attributes & Evidence */}
          <section className="space-y-3">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
              Enriched Attributes &amp; Supporting Evidence
            </h4>

            {Object.keys(attributes).length === 0 ? (
              <div className="text-xs text-zinc-500 p-4 rounded-lg bg-zinc-50 dark:bg-zinc-800/30 text-center">
                {record.status === 'PROCESSING'
                  ? 'Currently researching entity and verifying evidence...'
                  : 'No factual attributes enriched for this record.'}
              </div>
            ) : (
              <div className="space-y-3">
                {Object.entries(attributes).map(([attrKey, tuple]) => (
                  <div
                    key={attrKey}
                    className="p-3.5 rounded-lg border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900/50 space-y-2"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-xs capitalize text-zinc-900 dark:text-zinc-100">
                        {attrKey.replace(/_/g, ' ')}
                      </span>
                      <div className="flex items-center gap-2">
                        {tuple.conflictDetected && (
                          <span className="px-1.5 py-0.5 text-[10px] font-semibold rounded bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
                            Conflict Detected
                          </span>
                        )}
                        {tuple.corroboratingSources && tuple.corroboratingSources.length > 1 && (
                          <span className="px-1.5 py-0.5 text-[10px] font-semibold rounded bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
                            {tuple.corroboratingSources.length} sources
                          </span>
                        )}
                        {renderConfidenceBadge(tuple.confidence)}
                      </div>
                    </div>

                    <div className="text-sm text-zinc-800 dark:text-zinc-200 font-medium">
                      {tuple.value || <span className="italic text-zinc-400">UNKNOWN</span>}
                    </div>

                    {tuple.evidenceSnippet && (
                      <div className="text-xs text-zinc-500 dark:text-zinc-400 bg-zinc-50 dark:bg-zinc-800/60 p-2 rounded border-l-2 border-zinc-300 dark:border-zinc-700 italic">
                        &ldquo;{tuple.evidenceSnippet}&rdquo;
                      </div>
                    )}

                    {tuple.sourceUrl && (
                      <div className="text-[11px] text-zinc-400">
                        Source:{' '}
                        <a
                          href={tuple.sourceUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-blue-500 hover:underline break-all"
                        >
                          {tuple.sourceUrl}
                        </a>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </section>

          {/* Discovered Sources */}
          {sources.length > 0 && (
            <section className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-500">
                  Discovered &amp; Ranked Sources
                </h4>
                <span className="text-xs text-zinc-400">{sources.length} sources</span>
              </div>

              <div className="space-y-2">
                {sources.map((src, idx) => (
                  <div
                    key={idx}
                    className="p-3 rounded-lg bg-zinc-50 dark:bg-zinc-800/40 text-xs space-y-1"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="px-1.5 py-0.5 text-[10px] font-semibold rounded bg-zinc-200 dark:bg-zinc-700 text-zinc-700 dark:text-zinc-300">
                        {src.sourceType}
                      </span>
                      {src.relevance != null && (
                        <span className="text-[10px] font-mono text-zinc-400">
                          Relevance: {(src.relevance * 100).toFixed(0)}%
                        </span>
                      )}
                    </div>
                    <a
                      href={src.url}
                      target="_blank"
                      rel="noreferrer"
                      className="text-blue-500 hover:underline font-medium break-all block"
                    >
                      {src.title || src.url}
                    </a>
                    {src.snippet && (
                      <p className="text-zinc-500 dark:text-zinc-400 line-clamp-2">
                        {src.snippet}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* Sparse Original Input Row */}
          <section className="space-y-2 pt-2 border-t border-zinc-200 dark:border-zinc-800">
            <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
              Original Input Record
            </h4>
            <div className="grid grid-cols-2 gap-2 text-xs bg-zinc-50 dark:bg-zinc-800/30 p-3 rounded-lg font-mono">
              {Object.entries(record.originalData).map(([k, v]) => (
                <div key={k} className="overflow-hidden">
                  <span className="text-zinc-500 font-semibold">{k}:</span>{' '}
                  <span className="text-zinc-800 dark:text-zinc-200 truncate">{v || '—'}</span>
                </div>
              ))}
            </div>
          </section>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-zinc-200 dark:border-zinc-800 flex justify-end bg-zinc-50 dark:bg-zinc-800/30">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 hover:opacity-90 transition-opacity"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
