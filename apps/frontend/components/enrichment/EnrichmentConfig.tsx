"use client";

import React, { useState } from "react";
import { EntityType } from "@/types/research";
import { EnrichmentPlan } from "@/types/ai";
import { aiService } from "@/services/aiService";

interface EnrichmentConfigProps {
  entityType: EntityType;
  onEntityTypeChange: (type: EntityType) => void;
  userRequirement: string;
  onUserRequirementChange: (req: string) => void;
  datasetHeaders: string[];
  totalRows: number;
  onStartEnrichment: () => void;
  onBack: () => void;
  isSubmitting?: boolean;
}

const REQUIREMENT_PRESETS = [
  "Find current role, company, location, and educational degrees",
  "Extract tech stack, programming languages, and recent projects",
  "Identify executive leadership, corporate headquarters, and funding",
  "Extract verified social profile links and public repository URLs",
];

export function EnrichmentConfig({
  entityType,
  onEntityTypeChange,
  userRequirement,
  onUserRequirementChange,
  datasetHeaders,
  totalRows,
  onStartEnrichment,
  onBack,
  isSubmitting = false,
}: EnrichmentConfigProps) {
  const [isPlanning, setIsPlanning] = useState(false);
  const [plan, setPlan] = useState<EnrichmentPlan | null>(null);
  const [planError, setPlanError] = useState<string | null>(null);

  const handlePreviewPlan = async () => {
    try {
      setIsPlanning(true);
      setPlanError(null);
      const res = await aiService.planRequirement({
        userObjective: userRequirement.trim(),
        entityType,
        existingDatasetColumns: datasetHeaders,
      });
      setPlan(res);
    } catch (err: unknown) {
      setPlanError(err instanceof Error ? err.message : "Failed to generate AI enrichment plan");
    } finally {
      setIsPlanning(false);
    }
  };

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl p-6 shadow-xs space-y-6">
      <div>
        <h3 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
          Enrichment Strategy &amp; Objective
        </h3>
        <p className="text-xs text-zinc-500 dark:text-zinc-400 mt-1">
          Specify custom information to extract, or leave blank for the backend&apos;s standard profile discovery.
        </p>
      </div>

      {/* Target Entity Type */}
      <div className="space-y-1.5">
        <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
          Target Entity Type
        </label>
        <div className="flex flex-wrap gap-2">
          {(["PERSON", "ORGANIZATION", "PRODUCT", "REPOSITORY", "WEBSITE", "OTHER"] as EntityType[]).map(
            (t) => (
              <button
                key={t}
                type="button"
                onClick={() => onEntityTypeChange(t)}
                className={`px-3 py-1.5 text-xs font-medium rounded-lg border transition-colors ${
                  entityType === t
                    ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 border-zinc-900 dark:border-zinc-100"
                    : "bg-white dark:bg-zinc-900 text-zinc-600 dark:text-zinc-400 border-zinc-200 dark:border-zinc-700 hover:bg-zinc-50 dark:hover:bg-zinc-800"
                }`}
              >
                {t}
              </button>
            )
          )}
        </div>
      </div>

      {/* User Requirement Input */}
      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <label className="block text-xs font-semibold text-zinc-700 dark:text-zinc-300">
            Enrichment Requirement (Optional)
          </label>
          <span className="text-[11px] text-zinc-400 italic">
            Leave blank for standard entity profile
          </span>
        </div>

        <textarea
          rows={3}
          value={userRequirement}
          onChange={(e) => {
            onUserRequirementChange(e.target.value);
            setPlan(null);
          }}
          placeholder="e.g. Find current employer, current job title, university degrees, and city location..."
          className="w-full text-xs p-3 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100 placeholder:text-zinc-400"
        />

        {/* Suggestion Chips */}
        <div className="space-y-1.5 pt-1">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
            Suggestion Presets:
          </span>
          <div className="flex flex-wrap gap-1.5">
            {REQUIREMENT_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                onClick={() => {
                  onUserRequirementChange(preset);
                  setPlan(null);
                }}
                className="px-2.5 py-1 text-[11px] rounded-md bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 hover:bg-zinc-200 dark:hover:bg-zinc-700 transition-colors text-left"
              >
                &ldquo;{preset}&rdquo;
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Optional Plan Preview Trigger */}
      <div className="flex items-center justify-between pt-2 border-t border-zinc-100 dark:border-zinc-800">
        <button
          type="button"
          onClick={handlePreviewPlan}
          disabled={isPlanning}
          className="text-xs font-medium text-blue-600 dark:text-blue-400 hover:underline flex items-center gap-1.5 disabled:opacity-50"
        >
          {isPlanning ? (
            <>
              <span className="animate-spin inline-block w-3 h-3 border-2 border-current border-t-transparent rounded-full" />
              <span>Analyzing requirements with Spring AI...</span>
            </>
          ) : (
            <span>⚡ Preview AI Enrichment Plan</span>
          )}
        </button>

        <span className="text-[11px] text-zinc-400">
          Enriching {totalRows} {totalRows === 1 ? "record" : "records"}
        </span>
      </div>

      {/* Plan Preview Result Banner */}
      {plan && (
        <div className="p-4 bg-blue-50/60 dark:bg-blue-950/20 border border-blue-200 dark:border-blue-900 rounded-lg text-xs space-y-2">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-blue-900 dark:text-blue-200">
              {plan.userGoalSummary}
            </span>
            <span className="text-[10px] text-blue-700 dark:text-blue-300 font-mono">
              ~{plan.estimatedSourcesCount} sources/entity
            </span>
          </div>

          <div className="flex flex-wrap gap-1.5 pt-1">
            {plan.plannedFields.map((field) => (
              <span
                key={field.fieldKey}
                className="px-2 py-0.5 rounded bg-white dark:bg-zinc-900 border border-blue-200 dark:border-blue-800 text-[11px] text-zinc-700 dark:text-zinc-300 font-medium"
              >
                {field.displayName} &bull;{" "}
                <span className="text-zinc-400 text-[10px]">{field.strategy}</span>
              </span>
            ))}
          </div>
        </div>
      )}

      {planError && (
        <div className="p-3 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded-lg text-xs text-amber-800 dark:text-amber-200">
          Could not preview AI plan ({planError}). Backend default plan will be applied during execution.
        </div>
      )}

      {/* Action Buttons */}
      <div className="flex items-center justify-between pt-3 border-t border-zinc-100 dark:border-zinc-800">
        <button
          type="button"
          onClick={onBack}
          disabled={isSubmitting}
          className="px-4 py-2 text-xs font-medium text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-200 bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 rounded-lg transition-colors"
        >
          &larr; Back to Mapping
        </button>

        <button
          type="button"
          disabled={isSubmitting}
          onClick={onStartEnrichment}
          className="px-6 py-2.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 rounded-lg shadow-sm transition-all flex items-center gap-2"
        >
          {isSubmitting ? (
            <>
              <span className="animate-spin inline-block w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full" />
              <span>Submitting Job...</span>
            </>
          ) : (
            <span>🚀 Start Dataset Enrichment</span>
          )}
        </button>
      </div>
    </div>
  );
}
