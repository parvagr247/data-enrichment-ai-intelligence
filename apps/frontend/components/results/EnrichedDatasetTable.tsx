'use client';

import React, { useState, useMemo } from 'react';
import { EnrichedRecord, ColumnMapping, RowEnrichmentStatus } from '@/lib/api';

interface EnrichedDatasetTableProps {
  records: EnrichedRecord[];
  mapping: ColumnMapping;
  onSelectRecord: (record: EnrichedRecord) => void;
}

export const EnrichedDatasetTable: React.FC<EnrichedDatasetTableProps> = ({
  records,
  mapping,
  onSelectRecord,
}) => {
  const [filter, setFilter] = useState<'ALL' | RowEnrichmentStatus>('ALL');

  const counts = useMemo(() => {
    return records.reduce(
      (acc, r) => {
        acc[r.status] = (acc[r.status] || 0) + 1;
        return acc;
      },
      {} as Record<RowEnrichmentStatus, number>
    );
  }, [records]);

  const filteredRecords = useMemo(() => {
    if (filter === 'ALL') return records;
    return records.filter((r) => r.status === filter);
  }, [records, filter]);

  const renderStatusBadge = (status: RowEnrichmentStatus) => {
    switch (status) {
      case 'COMPLETED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
            COMPLETED
          </span>
        );
      case 'PARTIAL':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
            PARTIAL
          </span>
        );
      case 'FAILED':
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
            FAILED
          </span>
        );
      case 'PROCESSING':
        return (
          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-semibold bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300">
            <span className="animate-spin inline-block w-2.5 h-2.5 border-2 border-current border-t-transparent rounded-full" />
            PROCESSING
          </span>
        );
      case 'PENDING':
      default:
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
            PENDING
          </span>
        );
    }
  };

  const getConfidenceDot = (tier?: string) => {
    const t = (tier || 'LOW').toUpperCase();
    if (t === 'HIGH') return 'bg-emerald-500';
    if (t === 'MEDIUM') return 'bg-amber-500';
    return 'bg-rose-400';
  };

  return (
    <div className="space-y-4">
      {/* Header & Filter Controls */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white dark:bg-zinc-900 p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs">
        <div>
          <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            Enriched Dataset ({records.length} Records)
          </h3>
          <p className="text-xs text-zinc-500 mt-0.5">
            Grounded AI fact extraction and verifiable source evidence
          </p>
        </div>

        {/* Filter Pills */}
        <div className="flex items-center gap-1 text-xs">
          <button
            onClick={() => setFilter('ALL')}
            className={`px-2.5 py-1 rounded-lg font-medium transition-colors ${
              filter === 'ALL'
                ? 'bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900'
                : 'text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800'
            }`}
          >
            All ({records.length})
          </button>
          <button
            onClick={() => setFilter('COMPLETED')}
            className={`px-2.5 py-1 rounded-lg font-medium transition-colors ${
              filter === 'COMPLETED'
                ? 'bg-emerald-600 text-white'
                : 'text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800'
            }`}
          >
            Completed ({counts.COMPLETED || 0})
          </button>
          <button
            onClick={() => setFilter('PARTIAL')}
            className={`px-2.5 py-1 rounded-lg font-medium transition-colors ${
              filter === 'PARTIAL'
                ? 'bg-amber-600 text-white'
                : 'text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800'
            }`}
          >
            Partial ({counts.PARTIAL || 0})
          </button>
          <button
            onClick={() => setFilter('FAILED')}
            className={`px-2.5 py-1 rounded-lg font-medium transition-colors ${
              filter === 'FAILED'
                ? 'bg-rose-600 text-white'
                : 'text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800'
            }`}
          >
            Failed ({counts.FAILED || 0})
          </button>
        </div>
      </div>

      {/* Records Table */}
      <div className="bg-white dark:bg-zinc-900 rounded-xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-xs">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-zinc-50 dark:bg-zinc-800/50 border-b border-zinc-200 dark:border-zinc-800 font-semibold text-zinc-500 uppercase tracking-wider">
              <tr>
                <th className="p-3.5 w-12 text-center">#</th>
                <th className="p-3.5">Target Entity</th>
                <th className="p-3.5">Input Context</th>
                <th className="p-3.5">Status</th>
                <th className="p-3.5">Enriched Attributes</th>
                <th className="p-3.5 text-center">Sources</th>
                <th className="p-3.5 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
              {filteredRecords.length === 0 ? (
                <tr>
                  <td colSpan={7} className="p-8 text-center text-zinc-400">
                    No records found matching filter &ldquo;{filter}&rdquo;.
                  </td>
                </tr>
              ) : (
                filteredRecords.map((record) => {
                  const nameVal =
                    (mapping.nameColumn && record.originalData[mapping.nameColumn]) ||
                    record.response?.result?.displayName ||
                    'Unknown Entity';

                  const secondaryVal =
                    (mapping.organizationColumn && record.originalData[mapping.organizationColumn]) ||
                    (mapping.roleColumn && record.originalData[mapping.roleColumn]) ||
                    (mapping.urlColumn && record.originalData[mapping.urlColumn]) ||
                    '—';

                  const attributes = record.response?.result?.attributes || {};
                  const attrEntries = Object.entries(attributes);
                  const sourcesCount = record.response?.sources?.length || 0;

                  return (
                    <tr
                      key={record.id}
                      className="hover:bg-zinc-50/75 dark:hover:bg-zinc-800/40 transition-colors"
                    >
                      <td className="p-3.5 text-center text-zinc-400 font-mono">
                        {record.rowIndex + 1}
                      </td>

                      {/* Target Entity */}
                      <td className="p-3.5 font-medium text-zinc-900 dark:text-zinc-100">
                        <div className="font-semibold">{nameVal}</div>
                        {record.response?.result?.canonicalUrl && (
                          <div className="text-[11px] text-zinc-400 truncate max-w-[200px]">
                            {record.response.result.canonicalUrl}
                          </div>
                        )}
                      </td>

                      {/* Input Context */}
                      <td className="p-3.5 text-zinc-600 dark:text-zinc-300 truncate max-w-[180px]">
                        {secondaryVal}
                      </td>

                      {/* Status */}
                      <td className="p-3.5">{renderStatusBadge(record.status)}</td>

                      {/* Enriched Attributes Preview */}
                      <td className="p-3.5">
                        {attrEntries.length === 0 ? (
                          <span className="text-zinc-400 italic">
                            {record.status === 'PROCESSING' ? 'Researching...' : 'No attributes'}
                          </span>
                        ) : (
                          <div className="flex flex-wrap gap-1 max-w-sm">
                            {attrEntries.slice(0, 3).map(([key, tuple]) => (
                              <span
                                key={key}
                                className="inline-flex items-center gap-1.5 px-1.5 py-0.5 rounded bg-zinc-100 dark:bg-zinc-800 text-[11px] text-zinc-700 dark:text-zinc-300 border border-zinc-200/50 dark:border-zinc-700/50"
                                title={`${key}: ${tuple.value} [Confidence: ${tuple.confidence}${tuple.conflictDetected ? ' | CONFLICT' : ''}]`}
                              >
                                <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${getConfidenceDot(tuple.confidence)}`} />
                                <span className="font-semibold text-zinc-500 capitalize">
                                  {key}:
                                </span>
                                <span className="truncate max-w-[100px]">{tuple.value}</span>
                                {tuple.conflictDetected && (
                                  <span className="text-[10px] text-rose-500 font-bold" title="Conflict detected across sources">!</span>
                                )}
                              </span>
                            ))}
                            {attrEntries.length > 3 && (
                              <span className="text-[10px] text-zinc-400 self-center">
                                +{attrEntries.length - 3} more
                              </span>
                            )}
                          </div>
                        )}
                      </td>

                      {/* Sources Count */}
                      <td className="p-3.5 text-center font-mono text-zinc-500">
                        {sourcesCount > 0 ? (
                          <span className="px-2 py-0.5 rounded-full bg-zinc-100 dark:bg-zinc-800 text-[11px]">
                            {sourcesCount}
                          </span>
                        ) : (
                          '0'
                        )}
                      </td>

                      {/* Action */}
                      <td className="p-3.5 text-right">
                        <button
                          onClick={() => onSelectRecord(record)}
                          disabled={record.status === 'PENDING'}
                          className="px-3 py-1 rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 text-xs font-medium hover:opacity-90 disabled:opacity-30 transition-opacity"
                        >
                          Inspect Evidence
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
