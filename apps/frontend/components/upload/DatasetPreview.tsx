"use client";

import React from "react";
import { RawRow } from "@/lib/api";

interface DatasetPreviewProps {
  filename: string;
  totalRows: number;
  headers: string[];
  rows: RawRow[];
  maxPreviewRows?: number;
  onReset: () => void;
  onProceed: () => void;
}

export function DatasetPreview({
  filename,
  totalRows,
  headers,
  rows,
  maxPreviewRows = 15,
  onReset,
  onProceed,
}: DatasetPreviewProps) {
  const previewRows = rows.slice(0, maxPreviewRows);

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-5 shadow-sm space-y-4">
      {/* Header Info */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-100 dark:border-zinc-800 pb-3">
        <div>
          <div className="flex items-center gap-2">
            <span className="font-semibold text-zinc-900 dark:text-zinc-100 text-base">
              {filename}
            </span>
            <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300">
              {totalRows} {totalRows === 1 ? "record" : "records"}
            </span>
            <span className="px-2 py-0.5 text-xs font-medium rounded-full bg-zinc-100 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
              {headers.length} columns
            </span>
          </div>
          <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
            Showing first {previewRows.length} rows preview
          </p>
        </div>

        <div className="flex items-center gap-2">
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
            className="px-4 py-1.5 text-xs font-medium text-white bg-blue-600 hover:bg-blue-700 rounded-lg shadow-sm transition-colors flex items-center gap-1.5"
          >
            Confirm Columns & Enrich
            <svg
              className="w-3.5 h-3.5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 5l7 7-7 7"
              />
            </svg>
          </button>
        </div>
      </div>

      {/* Preview Table */}
      <div className="overflow-x-auto border border-zinc-200 dark:border-zinc-800 rounded-lg">
        <table className="min-w-full text-xs text-left">
          <thead className="bg-zinc-50 dark:bg-zinc-800/60 text-zinc-700 dark:text-zinc-300 font-semibold uppercase tracking-wider border-b border-zinc-200 dark:border-zinc-800">
            <tr>
              <th className="px-3 py-2.5 w-12 text-center text-zinc-400">#</th>
              {headers.map((header) => (
                <th key={header} className="px-3 py-2.5 whitespace-nowrap">
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800 font-normal">
            {previewRows.map((row, idx) => (
              <tr
                key={idx}
                className="hover:bg-zinc-50/70 dark:hover:bg-zinc-800/40 transition-colors"
              >
                <td className="px-3 py-2 text-center text-zinc-400 font-mono">
                  {idx + 1}
                </td>
                {headers.map((header) => {
                  const val = row[header];
                  return (
                    <td
                      key={header}
                      className="px-3 py-2 whitespace-nowrap max-w-xs truncate text-zinc-800 dark:text-zinc-200"
                      title={val || ""}
                    >
                      {val ? (
                        <span>{val}</span>
                      ) : (
                        <span className="text-zinc-300 dark:text-zinc-600 italic">
                          (empty)
                        </span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
