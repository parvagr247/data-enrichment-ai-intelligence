"use client";

import React, { useMemo } from "react";
import { EnrichedRecord, ColumnMapping, RowEnrichmentStatus } from "@/types/dataset";

interface EnrichedDatasetTableProps {
  records: EnrichedRecord[];
  mapping: ColumnMapping;
  onSelectRecord: (record: EnrichedRecord) => void;
  filter?: "ALL" | RowEnrichmentStatus;
}

export function EnrichedDatasetTable({
  records,
  mapping,
  onSelectRecord,
  filter = "ALL",
}: EnrichedDatasetTableProps) {
  // Filter records
  const filteredRecords = useMemo(() => {
    if (filter === "ALL") return records;
    return records.filter((r) => r.status === filter);
  }, [records, filter]);

  // Extract all enriched field keys across all records
  const enrichedFieldKeys = useMemo(() => {
    const keys = new Set<string>();
    records.forEach((r) => {
      if (r.attributes) {
        Object.keys(r.attributes).forEach((k) => keys.add(k));
      }
      if (r.response?.result?.attributes) {
        Object.keys(r.response.result.attributes).forEach((k) => keys.add(k));
      }
    });
    return Array.from(keys);
  }, [records]);

  // Determine original columns to display (using mapped columns first, then others)
  const originalColumns = useMemo(() => {
    if (records.length === 0) return [];
    const firstRow = records[0].originalData;
    const allCols = Object.keys(firstRow);
    const mapped = [
      mapping.nameColumn,
      mapping.organizationColumn,
      mapping.roleColumn,
      mapping.urlColumn,
    ].filter(Boolean) as string[];

    const remaining = allCols.filter((c) => !mapped.includes(c));
    return [...mapped, ...remaining].slice(0, 4); // Limit to top 4 original columns for table readability
  }, [records, mapping]);

  const renderStatusBadge = (status: RowEnrichmentStatus) => {
    switch (status) {
      case "COMPLETED":
        return (
          <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
            COMPLETED
          </span>
        );
      case "PARTIAL":
        return (
          <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
            PARTIAL
          </span>
        );
      case "FAILED":
        return (
          <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
            FAILED
          </span>
        );
      case "PROCESSING":
        return (
          <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300 flex items-center gap-1">
            <span className="animate-spin inline-block w-2 h-2 border-2 border-current border-t-transparent rounded-full" />
            RUNNING
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
            {status}
          </span>
        );
    }
  };

  const getAttributeValue = (record: EnrichedRecord, key: string) => {
    if (record.attributes && record.attributes[key]) {
      return record.attributes[key];
    }
    if (record.response?.result?.attributes && record.response.result.attributes[key]) {
      return record.response.result.attributes[key];
    }
    return null;
  };

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl overflow-hidden shadow-xs">
      <div className="p-4 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
        <div>
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
            Enriched Dataset View
          </h4>
          <p className="text-[11px] text-zinc-500 mt-0.5">
            Click any row or attribute pill to inspect grounded evidence, verbatim quotes, and source URLs.
          </p>
        </div>
        <span className="text-xs font-mono text-zinc-400">
          Showing {filteredRecords.length} records
        </span>
      </div>

      <div className="overflow-x-auto max-h-[600px]">
        <table className="w-full text-left text-xs border-collapse">
          <thead className="bg-zinc-50 dark:bg-zinc-800/80 border-b border-zinc-200 dark:border-zinc-800 sticky top-0 z-10 text-[11px] text-zinc-500 font-semibold">
            <tr>
              <th className="p-3 w-10 text-center text-zinc-400 font-mono">#</th>
              <th className="p-3 w-24">Status</th>

              {/* Original Columns Header Group */}
              {originalColumns.map((col) => (
                <th key={`orig-${col}`} className="p-3 text-zinc-700 dark:text-zinc-300">
                  <div className="flex flex-col">
                    <span className="truncate">{col}</span>
                    <span className="text-[9px] text-zinc-400 uppercase font-mono">Original</span>
                  </div>
                </th>
              ))}

              {/* Enriched Columns Header Group */}
              {enrichedFieldKeys.map((key) => (
                <th
                  key={`enrich-${key}`}
                  className="p-3 text-blue-900 dark:text-blue-200 bg-blue-50/40 dark:bg-blue-950/20 border-l border-blue-100 dark:border-blue-900/40"
                >
                  <div className="flex flex-col">
                    <span className="truncate capitalize">{key.replace(/_/g, " ")}</span>
                    <span className="text-[9px] text-blue-600 dark:text-blue-400 uppercase font-mono">
                      Enriched
                    </span>
                  </div>
                </th>
              ))}

              <th className="p-3 text-right">Evidence</th>
            </tr>
          </thead>

          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
            {filteredRecords.map((record, idx) => (
              <tr
                key={record.id || idx}
                onClick={() => onSelectRecord(record)}
                className="hover:bg-blue-50/30 dark:hover:bg-zinc-800/50 cursor-pointer transition-colors"
              >
                <td className="p-3 text-center text-zinc-400 font-mono text-[11px]">
                  {record.rowIndex + 1}
                </td>
                <td className="p-3 whitespace-nowrap">{renderStatusBadge(record.status)}</td>

                {/* Original Values */}
                {originalColumns.map((col) => {
                  const val = record.originalData[col];
                  return (
                    <td key={`orig-val-${col}`} className="p-3 text-zinc-800 dark:text-zinc-200 max-w-[180px] truncate">
                      {val || <span className="text-zinc-300 dark:text-zinc-600 italic">null</span>}
                    </td>
                  );
                })}

                {/* Enriched Values */}
                {enrichedFieldKeys.map((key) => {
                  const attr = getAttributeValue(record, key);
                  if (!attr) {
                    return (
                      <td
                        key={`enrich-val-${key}`}
                        className="p-3 bg-blue-50/20 dark:bg-blue-950/10 border-l border-blue-100/50 dark:border-blue-900/20 text-zinc-300 dark:text-zinc-600 italic"
                      >
                        —
                      </td>
                    );
                  }

                  const confidenceTier = (attr.confidence || "LOW").toUpperCase();
                  const badgeColor =
                    confidenceTier === "HIGH"
                      ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                      : confidenceTier === "MEDIUM"
                      ? "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300"
                      : "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400";

                  return (
                    <td
                      key={`enrich-val-${key}`}
                      className="p-3 bg-blue-50/20 dark:bg-blue-950/10 border-l border-blue-100/50 dark:border-blue-900/20 max-w-[220px]"
                    >
                      <div className="flex items-center justify-between gap-1.5">
                        <span className="text-zinc-900 dark:text-zinc-100 font-medium truncate">
                          {attr.value}
                        </span>
                        <span className={`px-1.5 py-0.2 text-[9px] font-semibold rounded shrink-0 ${badgeColor}`}>
                          {confidenceTier[0]}
                        </span>
                      </div>
                    </td>
                  );
                })}

                {/* Action button */}
                <td className="p-3 text-right whitespace-nowrap">
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelectRecord(record);
                    }}
                    className="px-2.5 py-1 text-[11px] font-medium rounded-md bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 text-zinc-700 dark:text-zinc-300 transition-colors"
                  >
                    Inspect
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
