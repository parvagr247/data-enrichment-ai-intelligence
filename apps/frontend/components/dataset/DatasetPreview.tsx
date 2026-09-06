"use client";

import React from "react";
import { DatasetProfileReport } from "@/types/dataset";

interface DatasetPreviewProps {
  report: DatasetProfileReport;
  onProceed: () => void;
  onReset: () => void;
}

export function DatasetPreview({ report, onProceed, onReset }: DatasetPreviewProps) {
  const headers = report.columns.map((c) => c.columnName);
  const previewRows = report.previewRows || [];

  const getQualityBadgeColor = (score: number) => {
    if (score >= 80) return "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800";
    if (score >= 60) return "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300 border-amber-200 dark:border-amber-800";
    return "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300 border-rose-200 dark:border-rose-800";
  };

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-5 shadow-xs space-y-5">
      {/* File & Quality Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-zinc-100 dark:border-zinc-800 pb-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-semibold text-zinc-900 dark:text-zinc-100 text-base">
              {report.datasetName}
            </span>
            <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300">
              {report.totalRows} {report.totalRows === 1 ? "row" : "rows"}
            </span>
            <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
              {report.columns.length} columns
            </span>
          </div>

          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1">
            Valid rows: <strong className="text-zinc-700 dark:text-zinc-300">{report.validRows}</strong>
            {report.malformedRowCount > 0 && (
              <span className="text-rose-600 dark:text-rose-400 ml-2">
                &bull; {report.malformedRowCount} malformed
              </span>
            )}
            {report.duplicateRowCount > 0 && (
              <span className="text-amber-600 dark:text-amber-400 ml-2">
                &bull; {report.duplicateRowCount} duplicates
              </span>
            )}
          </p>
        </div>

        {/* Quality Score & Actions */}
        <div className="flex items-center gap-3">
          <div
            className={`px-3 py-1.5 rounded-lg border text-xs font-medium flex items-center gap-1.5 ${getQualityBadgeColor(
              report.qualityScore
            )}`}
            title={report.qualityExplanation}
          >
            <span>Quality:</span>
            <strong className="font-bold">{report.qualityScore.toFixed(1)}/100</strong>
          </div>

          <button
            type="button"
            onClick={onReset}
            className="px-3 py-1.5 text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-200 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 rounded-lg transition-colors"
          >
            Change File
          </button>

          <button
            type="button"
            onClick={onProceed}
            className="px-4 py-1.5 text-xs font-semibold text-white bg-zinc-900 dark:bg-zinc-100 dark:text-zinc-900 hover:opacity-90 rounded-lg shadow-xs transition-opacity flex items-center gap-1.5"
          >
            <span>Continue to Mapping &rarr;</span>
          </button>
        </div>
      </div>

      {/* Quality Explanation Banner */}
      {report.qualityExplanation && (
        <div className="p-3 bg-zinc-50 dark:bg-zinc-800/50 rounded-lg text-xs text-zinc-600 dark:text-zinc-400 border border-zinc-100 dark:border-zinc-800">
          <span className="font-semibold text-zinc-800 dark:text-zinc-200">Backend Assessment:</span>{" "}
          {report.qualityExplanation}
        </div>
      )}

      {/* Conflicting Rows Alert if present */}
      {report.conflictingFields && report.conflictingFields.length > 0 && (
        <div className="p-3 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded-lg text-xs text-amber-800 dark:text-amber-200 space-y-1">
          <div className="font-semibold">Detected Dataset Conflicts:</div>
          <ul className="list-disc list-inside space-y-0.5">
            {report.conflictingFields.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Preview Table */}
      <div className="border border-zinc-200 dark:border-zinc-800 rounded-lg overflow-hidden">
        <div className="overflow-x-auto max-h-80">
          <table className="w-full text-left text-xs">
            <thead className="bg-zinc-50 dark:bg-zinc-800/70 border-b border-zinc-200 dark:border-zinc-800 sticky top-0 z-10">
              <tr>
                <th className="p-3 text-zinc-400 font-mono w-12 text-center">#</th>
                {report.columns.map((col) => (
                  <th key={col.columnName} className="p-3 font-semibold text-zinc-700 dark:text-zinc-300">
                    <div className="flex flex-col gap-0.5">
                      <span className="truncate">{col.columnName}</span>
                      <span className="text-[10px] font-mono text-zinc-400 font-normal">
                        {col.detectedRole} &bull; {col.completenessPercentage}%
                      </span>
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
              {previewRows.map((row, rowIdx) => (
                <tr key={rowIdx} className="hover:bg-zinc-50/60 dark:hover:bg-zinc-800/30 transition-colors">
                  <td className="p-3 text-center text-zinc-400 font-mono text-[11px]">
                    {rowIdx + 1}
                  </td>
                  {headers.map((header) => {
                    const val = row[header];
                    return (
                      <td key={header} className="p-3 text-zinc-800 dark:text-zinc-200 max-w-xs truncate">
                        {val && val.trim() !== "" ? (
                          val
                        ) : (
                          <span className="text-zinc-300 dark:text-zinc-600 italic">null</span>
                        )}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="p-2.5 bg-zinc-50 dark:bg-zinc-800/30 border-t border-zinc-200 dark:border-zinc-800 text-[11px] text-zinc-400 text-center">
          Showing preview of first {previewRows.length} rows of {report.totalRows} records
        </div>
      </div>
    </div>
  );
}
