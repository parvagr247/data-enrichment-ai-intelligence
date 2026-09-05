"use client";

import React from "react";
import { ColumnMapping, EntityType } from "@/lib/api";

interface ColumnConfirmationProps {
  headers: string[];
  mapping: ColumnMapping;
  onMappingChange: (mapping: ColumnMapping) => void;
  entityType: EntityType;
  onEntityTypeChange: (type: EntityType) => void;
  onStartEnrichment: () => void;
  onBack: () => void;
  isSubmitting?: boolean;
}

export function ColumnConfirmation({
  headers,
  mapping,
  onMappingChange,
  entityType,
  onEntityTypeChange,
  onStartEnrichment,
  onBack,
  isSubmitting = false,
}: ColumnConfirmationProps) {
  const hasIdentifier = Boolean(mapping.nameColumn || mapping.urlColumn);

  const handleFieldChange = (key: keyof ColumnMapping, val: string) => {
    onMappingChange({
      ...mapping,
      [key]: val === "__none__" ? undefined : val,
    });
  };

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-6 shadow-sm space-y-6">
      <div>
        <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
          Confirm Column Mappings
        </h3>
        <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-0.5">
          Review the detected identity columns. At least one of{" "}
          <strong className="text-zinc-700 dark:text-zinc-300">Name</strong> or{" "}
          <strong className="text-zinc-700 dark:text-zinc-300">Profile / URL</strong>{" "}
          must be selected for research discovery.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* Name Column */}
        <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/30">
          <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
            Name Column
            <span className="text-xs font-normal text-zinc-400 ml-1.5">
              (Primary search query)
            </span>
          </label>
          <select
            value={mapping.nameColumn || "__none__"}
            onChange={(e) => handleFieldChange("nameColumn", e.target.value)}
            disabled={isSubmitting}
            className="w-full text-xs bg-white dark:bg-zinc-900 border border-zinc-300 dark:border-zinc-700 rounded-md p-2 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="__none__">-- Do not map --</option>
            {headers.map((h) => (
              <option key={h} value={h}>
                {h}
              </option>
            ))}
          </select>
        </div>

        {/* URL Column */}
        <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/30">
          <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
            Profile / URL Column
            <span className="text-xs font-normal text-zinc-400 ml-1.5">
              (Strong identity anchor, e.g. LinkedIn)
            </span>
          </label>
          <select
            value={mapping.urlColumn || "__none__"}
            onChange={(e) => handleFieldChange("urlColumn", e.target.value)}
            disabled={isSubmitting}
            className="w-full text-xs bg-white dark:bg-zinc-900 border border-zinc-300 dark:border-zinc-700 rounded-md p-2 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="__none__">-- Do not map --</option>
            {headers.map((h) => (
              <option key={h} value={h}>
                {h}
              </option>
            ))}
          </select>
        </div>

        {/* Organization Column */}
        <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/30">
          <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
            Organization Column
            <span className="text-xs font-normal text-zinc-400 ml-1.5">
              (Company context / disambiguation)
            </span>
          </label>
          <select
            value={mapping.organizationColumn || "__none__"}
            onChange={(e) => handleFieldChange("organizationColumn", e.target.value)}
            disabled={isSubmitting}
            className="w-full text-xs bg-white dark:bg-zinc-900 border border-zinc-300 dark:border-zinc-700 rounded-md p-2 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="__none__">-- Do not map --</option>
            {headers.map((h) => (
              <option key={h} value={h}>
                {h}
              </option>
            ))}
          </select>
        </div>

        {/* Role Column */}
        <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/30">
          <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
            Role / Job Title Column
            <span className="text-xs font-normal text-zinc-400 ml-1.5">
              (Job title / position context)
            </span>
          </label>
          <select
            value={mapping.roleColumn || "__none__"}
            onChange={(e) => handleFieldChange("roleColumn", e.target.value)}
            disabled={isSubmitting}
            className="w-full text-xs bg-white dark:bg-zinc-900 border border-zinc-300 dark:border-zinc-700 rounded-md p-2 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="__none__">-- Do not map --</option>
            {headers.map((h) => (
              <option key={h} value={h}>
                {h}
              </option>
            ))}
          </select>
        </div>

        {/* Entity Type Selector */}
        <div className="p-3.5 border border-zinc-200 dark:border-zinc-800 rounded-lg bg-zinc-50/50 dark:bg-zinc-800/30 md:col-span-2">
          <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300 mb-1">
            Target Entity Type
            <span className="text-xs font-normal text-zinc-400 ml-1.5">
              (Instructs the Research Engine heuristics)
            </span>
          </label>
          <div className="flex flex-wrap gap-2">
            {(["PERSON", "ORGANIZATION", "PRODUCT", "REPOSITORY", "WEBSITE", "OTHER"] as EntityType[]).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => onEntityTypeChange(t)}
                disabled={isSubmitting}
                className={`px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                  entityType === t
                    ? "bg-blue-600 text-white shadow-sm"
                    : "bg-white dark:bg-zinc-900 border border-zinc-300 dark:border-zinc-700 text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                }`}
              >
                {t}
              </button>
            ))}
          </div>
        </div>
      </div>

      {!hasIdentifier && (
        <div className="p-3 bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900 rounded-lg text-xs text-red-700 dark:text-red-300">
          ⚠️ Please select at least one column for <strong>Name</strong> or <strong>Profile / URL</strong> so records can be resolved.
        </div>
      )}

      <div className="flex items-center justify-between pt-2 border-t border-zinc-100 dark:border-zinc-800">
        <button
          type="button"
          onClick={onBack}
          disabled={isSubmitting}
          className="px-3.5 py-2 text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-200 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 rounded-lg transition-colors"
        >
          ← Back to Preview
        </button>

        <button
          type="button"
          onClick={onStartEnrichment}
          disabled={!hasIdentifier || isSubmitting}
          className={`px-5 py-2 text-xs font-medium text-white rounded-lg shadow-sm transition-all flex items-center gap-2 ${
            !hasIdentifier || isSubmitting
              ? "bg-zinc-300 dark:bg-zinc-700 cursor-not-allowed opacity-70"
              : "bg-blue-600 hover:bg-blue-700 cursor-pointer"
          }`}
        >
          {isSubmitting ? (
            <>
              <svg
                className="animate-spin -ml-1 mr-2 h-4 w-4 text-white"
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
              Starting Enrichment...
            </>
          ) : (
            <>
              Start Enrichment
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M13 10V3L4 14h7v7l9-11h-7z"
                />
              </svg>
            </>
          )}
        </button>
      </div>
    </div>
  );
}
