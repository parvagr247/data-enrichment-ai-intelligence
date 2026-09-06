"use client";

import React, { useMemo, useState } from "react";
import {
  EnrichedRecord,
  ColumnMapping,
  RowEnrichmentStatus,
  PriorityTier,
  ApproachType,
} from "@/types/dataset";

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
  const [viewMode, setViewMode] = useState<"prioritized" | "raw">("prioritized");
  const [tierFilter, setTierFilter] = useState<"ALL" | PriorityTier>("ALL");
  const [sortBy, setSortBy] = useState<"score_desc" | "score_asc" | "row_asc">("score_desc");

  // Filter records by execution status and priority tier
  const filteredRecords = useMemo(() => {
    let list = records;
    if (filter !== "ALL") {
      list = list.filter((r) => r.status === filter);
    }
    if (tierFilter !== "ALL") {
      list = list.filter((r) => {
        const tier = r.assessment?.priorityTier || "NONE";
        return tier === tierFilter;
      });
    }

    return [...list].sort((a, b) => {
      if (sortBy === "score_desc") {
        const scoreA = a.assessment?.overallScore ?? Math.round((a.confidence || 0) * 100);
        const scoreB = b.assessment?.overallScore ?? Math.round((b.confidence || 0) * 100);
        return scoreB - scoreA;
      }
      if (sortBy === "score_asc") {
        const scoreA = a.assessment?.overallScore ?? Math.round((a.confidence || 0) * 100);
        const scoreB = b.assessment?.overallScore ?? Math.round((b.confidence || 0) * 100);
        return scoreA - scoreB;
      }
      return a.rowIndex - b.rowIndex;
    });
  }, [records, filter, tierFilter, sortBy]);

  // Extract all enriched field keys across all records for the raw column view
  const enrichedFieldKeys = useMemo(() => {
    const keys = new Set<string>();
    records.forEach((r) => {
      if (r.attributes) {
        Object.keys(r.attributes).forEach((k) => keys.add(k));
      }
    });
    return Array.from(keys);
  }, [records]);

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
    return [...mapped, ...remaining].slice(0, 4);
  }, [records, mapping]);

  const renderTierBadge = (tier?: PriorityTier) => {
    switch (tier) {
      case "HIGH":
        return (
          <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 border border-emerald-300 dark:border-emerald-800">
            HIGH
          </span>
        );
      case "MEDIUM":
        return (
          <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300 border border-amber-300 dark:border-amber-800">
            MEDIUM
          </span>
        );
      case "LOW":
        return (
          <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border border-zinc-300 dark:border-zinc-700">
            LOW
          </span>
        );
      default:
        return (
          <span className="px-2 py-0.5 text-[10px] font-medium rounded-full bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400">
            NONE
          </span>
        );
    }
  };

  const renderApproachBadge = (type?: ApproachType) => {
    if (!type) return null;
    const formatted = type.replace(/_/g, " ");
    return (
      <span className="px-2 py-0.5 text-[10px] font-semibold rounded bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800">
        {formatted}
      </span>
    );
  };

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

  return (
    <div className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-xl overflow-hidden shadow-xs space-y-0">
      {/* TOOLBAR & CONTROLS */}
      <div className="p-4 border-b border-zinc-200 dark:border-zinc-800 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h4 className="text-sm font-semibold text-zinc-900 dark:text-zinc-100 flex items-center gap-2">
            <span>Research Results & Intelligence</span>
            <span className="text-xs font-normal text-zinc-500 font-mono">
              ({filteredRecords.length} records)
            </span>
          </h4>
          <p className="text-[11px] text-zinc-500 mt-0.5">
            Prioritized by objective relevance. Click any record to inspect deep career profile, talking points & evidence.
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap self-start sm:self-auto">
          {/* View Mode Toggle */}
          <div className="inline-flex rounded-lg border border-zinc-200 dark:border-zinc-800 p-0.5 bg-zinc-50 dark:bg-zinc-800 text-xs">
            <button
              type="button"
              onClick={() => setViewMode("prioritized")}
              className={`px-2.5 py-1 rounded-md font-medium transition-colors ${
                viewMode === "prioritized"
                  ? "bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 shadow-xs"
                  : "text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
              }`}
            >
              Prioritized View
            </button>
            <button
              type="button"
              onClick={() => setViewMode("raw")}
              className={`px-2.5 py-1 rounded-md font-medium transition-colors ${
                viewMode === "raw"
                  ? "bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 shadow-xs"
                  : "text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
              }`}
            >
              Raw Attributes Grid
            </button>
          </div>

          {/* Tier Filter */}
          {viewMode === "prioritized" && (
            <select
              value={tierFilter}
              onChange={(e) => setTierFilter(e.target.value as any)}
              className="text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg px-2.5 py-1 bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300"
            >
              <option value="ALL">All Tiers</option>
              <option value="HIGH">High Priority Only</option>
              <option value="MEDIUM">Medium Priority Only</option>
              <option value="LOW">Low Priority Only</option>
            </select>
          )}

          {/* Sort By */}
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as any)}
            className="text-xs border border-zinc-200 dark:border-zinc-800 rounded-lg px-2.5 py-1 bg-white dark:bg-zinc-900 text-zinc-700 dark:text-zinc-300 font-mono"
          >
            <option value="score_desc">Sort: Highest Score</option>
            <option value="score_asc">Sort: Lowest Score</option>
            <option value="row_asc">Sort: Original Row Order</option>
          </select>
        </div>
      </div>

      {/* PRIORITIZED VIEW (HIGH SIGNAL SUMMARY) */}
      {viewMode === "prioritized" ? (
        <div className="overflow-x-auto max-h-[640px]">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="bg-zinc-50 dark:bg-zinc-800/80 border-b border-zinc-200 dark:border-zinc-800 sticky top-0 z-10 text-[11px] text-zinc-500 font-semibold">
              <tr>
                <th className="p-3 w-16 text-center">Tier</th>
                <th className="p-3 min-w-[180px]">Entity</th>
                <th className="p-3 min-w-[200px]">Current Role & Org</th>
                <th className="p-3 min-w-[220px]">Relevance & Assessment</th>
                <th className="p-3 min-w-[220px]">Recommended Outreach</th>
                <th className="p-3 min-w-[160px]">Key Expertise</th>
                <th className="p-3 text-right w-24">Action</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
              {filteredRecords.map((record, idx) => {
                const profile = record.profile;
                const assessment = record.assessment;
                const recommendation = record.recommendation;

                const role = profile?.currentRole || record.attributes?.currentRole?.value || "UNKNOWN";
                const org = profile?.currentOrganization || record.attributes?.currentOrganization?.value || "UNKNOWN";
                const location = profile?.location || record.attributes?.location?.value;

                const score = assessment?.overallScore ?? Math.round((record.confidence || 0) * 100);
                const whyRelevant = assessment?.whyRelevant || "Evaluated based on extracted background criteria.";
                const approach = recommendation?.approachType;
                const approachSummary = recommendation?.summary || "Direct professional networking";

                const skills = profile?.technicalExpertise?.length
                  ? profile.technicalExpertise
                  : record.attributes?.skills?.value
                  ? record.attributes.skills.value.split(",").map((s) => s.trim())
                  : [];

                return (
                  <tr
                    key={record.id || idx}
                    onClick={() => onSelectRecord(record)}
                    className="hover:bg-blue-50/40 dark:hover:bg-zinc-800/60 cursor-pointer transition-colors group"
                  >
                    {/* Priority Tier */}
                    <td className="p-3 text-center whitespace-nowrap">
                      {renderTierBadge(assessment?.priorityTier)}
                    </td>

                    {/* Entity Name & Canonical link */}
                    <td className="p-3">
                      <div className="flex flex-col">
                        <span className="font-semibold text-zinc-900 dark:text-zinc-100 group-hover:text-blue-600 transition-colors">
                          {record.displayName || "Unknown"}
                        </span>
                        {record.canonicalUrl ? (
                          <a
                            href={record.canonicalUrl}
                            target="_blank"
                            rel="noreferrer"
                            onClick={(e) => e.stopPropagation()}
                            className="text-[10px] text-blue-600 dark:text-blue-400 hover:underline truncate max-w-[180px] font-mono mt-0.5"
                          >
                            {record.canonicalUrl.replace(/^https?:\/\/(www\.)?/, "")}
                          </a>
                        ) : (
                          <span className="text-[10px] text-zinc-400">No canonical link</span>
                        )}
                      </div>
                    </td>

                    {/* Current Role & Organization */}
                    <td className="p-3">
                      <div className="flex flex-col">
                        <span className="font-medium text-zinc-800 dark:text-zinc-200">
                          {role !== "UNKNOWN" ? role : <span className="text-zinc-400 italic">—</span>}
                        </span>
                        <div className="flex items-center gap-1.5 text-[11px] text-zinc-500 mt-0.5">
                          {org !== "UNKNOWN" ? (
                            <strong className="text-zinc-700 dark:text-zinc-300 font-semibold">{org}</strong>
                          ) : (
                            <span className="italic">Organization unknown</span>
                          )}
                          {location && location !== "UNKNOWN" && (
                            <span className="truncate">📍 {location}</span>
                          )}
                        </div>
                      </div>
                    </td>

                    {/* Relevance & Assessment */}
                    <td className="p-3">
                      <div className="space-y-1.5 max-w-[260px]">
                        <div className="flex items-center gap-2">
                          <span className="font-mono text-xs font-bold text-blue-600 dark:text-blue-400">
                            {score}/100
                          </span>
                          <div className="w-16 bg-zinc-200 dark:bg-zinc-700 h-1.5 rounded-full overflow-hidden">
                            <div
                              className="bg-blue-600 dark:bg-blue-500 h-full rounded-full"
                              style={{ width: `${Math.min(100, Math.max(0, score))}%` }}
                            />
                          </div>
                        </div>
                        <p className="text-[11px] text-zinc-600 dark:text-zinc-300 line-clamp-2 leading-tight">
                          {whyRelevant}
                        </p>
                      </div>
                    </td>

                    {/* Recommended Outreach */}
                    <td className="p-3">
                      <div className="space-y-1 max-w-[240px]">
                        {approach && <div>{renderApproachBadge(approach)}</div>}
                        <p className="text-[11px] text-zinc-600 dark:text-zinc-300 line-clamp-2">
                          {approachSummary}
                        </p>
                      </div>
                    </td>

                    {/* Key Expertise */}
                    <td className="p-3">
                      <div className="flex flex-wrap gap-1 max-w-[180px]">
                        {skills.slice(0, 3).map((skill, i) => (
                          <span
                            key={i}
                            className="px-1.5 py-0.5 text-[10px] rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300"
                          >
                            {skill}
                          </span>
                        ))}
                        {skills.length > 3 && (
                          <span className="text-[10px] text-zinc-400">
                            +{skills.length - 3} more
                          </span>
                        )}
                        {skills.length === 0 && (
                          <span className="text-[11px] text-zinc-400 italic">—</span>
                        )}
                      </div>
                    </td>

                    {/* Action */}
                    <td className="p-3 text-right whitespace-nowrap">
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          onSelectRecord(record);
                        }}
                        className="px-2.5 py-1 text-[11px] font-semibold rounded-md bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300 hover:bg-blue-100 dark:hover:bg-blue-900 transition-colors"
                      >
                        Inspect Profile →
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        /* RAW ATTRIBUTES GRID VIEW */
        <div className="overflow-x-auto max-h-[640px]">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="bg-zinc-50 dark:bg-zinc-800/80 border-b border-zinc-200 dark:border-zinc-800 sticky top-0 z-10 text-[11px] text-zinc-500 font-semibold">
              <tr>
                <th className="p-3 w-10 text-center text-zinc-400 font-mono">#</th>
                <th className="p-3 w-24">Status</th>
                {originalColumns.map((col) => (
                  <th key={`orig-${col}`} className="p-3 text-zinc-700 dark:text-zinc-300">
                    <div className="flex flex-col">
                      <span className="truncate">{col}</span>
                      <span className="text-[9px] text-zinc-400 uppercase font-mono">Original</span>
                    </div>
                  </th>
                ))}
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
                <th className="p-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
              {filteredRecords.map((record, idx) => (
                <tr
                  key={record.id || idx}
                  onClick={() => onSelectRecord(record)}
                  className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/50 cursor-pointer"
                >
                  <td className="p-3 text-center text-zinc-400 font-mono text-[11px]">
                    {record.rowIndex + 1}
                  </td>
                  <td className="p-3 whitespace-nowrap">{renderStatusBadge(record.status)}</td>
                  {originalColumns.map((col) => (
                    <td key={`orig-val-${col}`} className="p-3 text-zinc-800 dark:text-zinc-200 max-w-[160px] truncate">
                      {record.originalData[col] || <span className="text-zinc-300 dark:text-zinc-600 italic">null</span>}
                    </td>
                  ))}
                  {enrichedFieldKeys.map((key) => {
                    const attr = record.attributes?.[key];
                    return (
                      <td
                        key={`enrich-val-${key}`}
                        className="p-3 bg-blue-50/20 dark:bg-blue-950/10 border-l border-blue-100/50 dark:border-blue-900/20 max-w-[200px]"
                      >
                        {attr ? (
                          <div className="flex items-center justify-between gap-1">
                            <span className="text-zinc-900 dark:text-zinc-100 truncate font-medium">
                              {attr.value}
                            </span>
                            <span className="text-[9px] font-mono text-zinc-400">
                              {attr.confidence}
                            </span>
                          </div>
                        ) : (
                          <span className="text-zinc-300 dark:text-zinc-600 italic">—</span>
                        )}
                      </td>
                    );
                  })}
                  <td className="p-3 text-right whitespace-nowrap">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelectRecord(record);
                      }}
                      className="px-2.5 py-1 text-[11px] font-medium rounded-md bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700"
                    >
                      Inspect
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
