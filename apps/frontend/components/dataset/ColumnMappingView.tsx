"use client";

import React from "react";
import { DatasetProfileReport, ColumnMapping } from "@/types/dataset";

interface ColumnMappingViewProps {
  report: DatasetProfileReport;
  mapping: ColumnMapping;
  onMappingChange: (mapping: ColumnMapping) => void;
  onProceed: () => void;
  onBack: () => void;
}

export function ColumnMappingView({
  report,
  mapping,
  onMappingChange,
  onProceed,
  onBack,
}: ColumnMappingViewProps) {
  const headers = report.columns.map((c) => c.columnName);
  const hasIdentifier = Boolean(
    mapping.nameColumn ||
      mapping.fullNameColumn ||
      (mapping.firstNameColumn && mapping.lastNameColumn) ||
      mapping.firstNameColumn ||
      mapping.urlColumn
  );

  const handleFieldChange = (key: keyof ColumnMapping, val: string) => {
    onMappingChange({
      ...mapping,
      [key]: val === "__none__" ? undefined : val,
    });
  };

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-6 shadow-xs space-y-6">
      <div>
        <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
          Dataset Column Understanding &amp; Composite Identity Mapping
        </h3>
        <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1">
          The backend automatically detected the semantic roles below. Verify or adjust the anchor fields used to synthesize entity identity and research context.
        </p>
      </div>

      {/* Backend Detected Roles Summary */}
      <div className="p-4 bg-zinc-50 dark:bg-zinc-800/40 rounded-lg border border-zinc-100 dark:border-zinc-800 space-y-3">
        <h4 className="text-xs font-semibold text-zinc-700 dark:text-zinc-300 uppercase tracking-wider">
          Detected Column Profiling
        </h4>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
          {report.columns.map((col) => (
            <div
              key={col.columnName}
              className="p-2.5 rounded bg-white dark:bg-zinc-900 border border-zinc-200/80 dark:border-zinc-800 flex items-center justify-between text-xs"
            >
              <div className="min-w-0 pr-2">
                <div className="font-medium text-zinc-800 dark:text-zinc-200 truncate" title={col.columnName}>
                  {col.columnName}
                </div>
                <div className="text-[10px] text-zinc-400">
                  Completeness: {col.completenessPercentage}%
                </div>
              </div>
              <span className="px-2 py-0.5 text-[10px] font-mono font-semibold rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300 shrink-0">
                {col.detectedRole}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Mapping Selectors - Composite Entity Identity */}
      <div className="space-y-4">
        {/* Name Identification Section */}
        <div className="p-4 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/20 space-y-3">
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div>
              <h4 className="text-xs font-semibold text-zinc-800 dark:text-zinc-200">
                Entity Name Anchoring
              </h4>
              <p className="text-[11px] text-zinc-500 dark:text-zinc-400">
                Map a combined Full Name column, or separate First and Last Name columns (automatically combined).
              </p>
            </div>
            {(mapping.firstNameColumn || mapping.lastNameColumn) && (
              <span className="px-2 py-0.5 text-[10px] font-mono rounded bg-blue-50 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800">
                Composite: [{mapping.firstNameColumn || "..."}] + [{mapping.lastNameColumn || "..."}]
              </span>
            )}
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400 mb-1">
                Full Name / Entity Name
              </label>
              <select
                value={mapping.fullNameColumn || mapping.nameColumn || "__none__"}
                onChange={(e) => {
                  const val = e.target.value === "__none__" ? undefined : e.target.value;
                  onMappingChange({ ...mapping, fullNameColumn: val, nameColumn: val });
                }}
                className="w-full text-xs px-2.5 py-1.5 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
              >
                <option value="__none__">&mdash; Not Mapped &mdash;</option>
                {headers.map((h) => (
                  <option key={h} value={h}>
                    {h}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400 mb-1">
                First Name Column
              </label>
              <select
                value={mapping.firstNameColumn || "__none__"}
                onChange={(e) => handleFieldChange("firstNameColumn", e.target.value)}
                className="w-full text-xs px-2.5 py-1.5 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
              >
                <option value="__none__">&mdash; Not Mapped &mdash;</option>
                {headers.map((h) => (
                  <option key={h} value={h}>
                    {h}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-medium text-zinc-600 dark:text-zinc-400 mb-1">
                Last Name Column
              </label>
              <select
                value={mapping.lastNameColumn || "__none__"}
                onChange={(e) => handleFieldChange("lastNameColumn", e.target.value)}
                className="w-full text-xs px-2.5 py-1.5 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
              >
                <option value="__none__">&mdash; Not Mapped &mdash;</option>
                {headers.map((h) => (
                  <option key={h} value={h}>
                    {h}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Core Identity & Context Anchors */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Profile / URL Column */}
          <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/20">
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
              Profile / URL Column
              <span className="text-[10px] font-normal text-zinc-400 ml-1.5">(LinkedIn, web profile, or repo)</span>
            </label>
            <select
              value={mapping.urlColumn || "__none__"}
              onChange={(e) => handleFieldChange("urlColumn", e.target.value)}
              className="w-full text-xs px-3 py-2 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
            >
              <option value="__none__">&mdash; Not Mapped &mdash;</option>
              {headers.map((h) => (
                <option key={h} value={h}>
                  {h}
                </option>
              ))}
            </select>
          </div>

          {/* Organization Column */}
          <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/20">
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
              Organization Column
              <span className="text-[10px] font-normal text-zinc-400 ml-1.5">(Company / Employer / College anchor)</span>
            </label>
            <select
              value={mapping.organizationColumn || "__none__"}
              onChange={(e) => handleFieldChange("organizationColumn", e.target.value)}
              className="w-full text-xs px-3 py-2 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
            >
              <option value="__none__">&mdash; Not Mapped &mdash;</option>
              {headers.map((h) => (
                <option key={h} value={h}>
                  {h}
                </option>
              ))}
            </select>
          </div>

          {/* Role Column */}
          <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/20">
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
              Role / Position Column
              <span className="text-[10px] font-normal text-zinc-400 ml-1.5">(Current title / role)</span>
            </label>
            <select
              value={mapping.roleColumn || "__none__"}
              onChange={(e) => handleFieldChange("roleColumn", e.target.value)}
              className="w-full text-xs px-3 py-2 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
            >
              <option value="__none__">&mdash; Not Mapped &mdash;</option>
              {headers.map((h) => (
                <option key={h} value={h}>
                  {h}
                </option>
              ))}
            </select>
          </div>

          {/* Optional Additional Anchors (Email / Location) */}
          <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/20">
            <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
              Secondary Anchors
              <span className="text-[10px] font-normal text-zinc-400 ml-1.5">(Email / Location context)</span>
            </label>
            <div className="grid grid-cols-2 gap-2 mt-1">
              <div>
                <label className="block text-[10px] text-zinc-500 mb-0.5">Email</label>
                <select
                  value={mapping.emailColumn || "__none__"}
                  onChange={(e) => handleFieldChange("emailColumn", e.target.value)}
                  className="w-full text-xs px-2 py-1.5 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
                >
                  <option value="__none__">&mdash; None &mdash;</option>
                  {headers.map((h) => (
                    <option key={h} value={h}>
                      {h}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-[10px] text-zinc-500 mb-0.5">Location</label>
                <select
                  value={mapping.locationColumn || "__none__"}
                  onChange={(e) => handleFieldChange("locationColumn", e.target.value)}
                  className="w-full text-xs px-2 py-1.5 rounded-md border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
                >
                  <option value="__none__">&mdash; None &mdash;</option>
                  {headers.map((h) => (
                    <option key={h} value={h}>
                      {h}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Informational Banner */}
      <div className="p-3 rounded-lg bg-zinc-50 dark:bg-zinc-800/50 border border-zinc-200/70 dark:border-zinc-800 text-zinc-600 dark:text-zinc-400 text-xs flex items-center gap-2">
        <span>💡</span>
        <span>
          <strong>Composite Entity Identity</strong>: These anchors are dynamically combined by the research engine to construct targeted discovery queries and prevent ambiguous or incorrect entity matches.
        </span>
      </div>

      {/* Validation Warning */}
      {!hasIdentifier && (
        <div className="p-3 rounded-lg bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900 text-amber-800 dark:text-amber-200 text-xs">
          ⚠️ At least one identity anchor column (<strong>Full Name</strong>, <strong>First Name</strong>, or <strong>Profile / URL</strong>) must be mapped to enable research.
        </div>
      )}

      {/* Navigation Actions */}
      <div className="flex items-center justify-between pt-2 border-t border-zinc-100 dark:border-zinc-800">
        <button
          type="button"
          onClick={onBack}
          className="px-4 py-2 text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-200 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 rounded-lg transition-colors"
        >
          &larr; Back to Preview
        </button>

        <button
          type="button"
          disabled={!hasIdentifier}
          onClick={onProceed}
          className="px-5 py-2 text-xs font-semibold text-white bg-zinc-900 dark:bg-zinc-100 dark:text-zinc-900 hover:opacity-90 disabled:opacity-40 rounded-lg shadow-xs transition-opacity"
        >
          Configure Enrichment &rarr;
        </button>
      </div>
    </div>
  );
}
