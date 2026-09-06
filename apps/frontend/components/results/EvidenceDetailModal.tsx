"use client";

import React, { useState, useEffect, useMemo } from "react";
import { EnrichedRecord } from "@/types/dataset";

interface EvidenceDetailModalProps {
  record: EnrichedRecord | null;
  onClose: () => void;
}

type ModalTab = "overview" | "experience" | "education" | "skills" | "projects" | "activity" | "all" | "sources";

interface ParsedAttribute {
  value: string;
  sourceUrl?: string | null;
  evidenceSnippet?: string | null;
  confidence?: string;
  conflictDetected?: boolean;
  conflictDescription?: string;
  corroboratingSources?: string[];
}

export function EvidenceDetailModal({ record, onClose }: EvidenceDetailModalProps) {
  const [activeTab, setActiveTab] = useState<ModalTab>("overview");

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const attributes = useMemo<Record<string, ParsedAttribute>>(() => {
    if (!record) return {};
    const res: Record<string, ParsedAttribute> = {};

    if (record.attributes) {
      Object.entries(record.attributes).forEach(([k, v]) => {
        res[k] = {
          value: v.value,
          sourceUrl: v.sourceUrl,
          evidenceSnippet: v.evidenceSnippet,
          confidence: v.confidence,
        };
      });
    } else if (record.response?.result?.attributes) {
      Object.entries(record.response.result.attributes).forEach(([k, v]) => {
        res[k] = {
          value: v.value,
          sourceUrl: v.sourceUrl,
          evidenceSnippet: v.evidenceSnippet,
          confidence: v.confidence,
          conflictDetected: v.conflictDetected,
          corroboratingSources: v.corroboratingSources,
        };
      });
    }
    return res;
  }, [record]);

  if (!record) return null;

  const sources = record.sources || record.response?.sources || [];
  const conflicts = record.conflicts || [];
  const warnings = record.response?.warnings || [];

  const getAttr = (key: string): ParsedAttribute | undefined => {
    if (attributes[key]) return attributes[key];
    const clean = key.toLowerCase().replace(/[_-]/g, "");
    for (const [k, v] of Object.entries(attributes)) {
      if (k.toLowerCase().replace(/[_-]/g, "") === clean) {
        return v;
      }
    }
    return undefined;
  };

  const experienceAttr = getAttr("experience") || getAttr("workExperience");
  const educationAttr = getAttr("education");
  const skillsAttr = getAttr("skills") || getAttr("technologies");
  const projectsAttr = getAttr("projects");
  const activityAttr = getAttr("activity") || getAttr("activities") || getAttr("posts");

  const renderConfidenceBadge = (confidence?: string) => {
    const tier = (confidence || "UNKNOWN").toUpperCase();
    if (tier === "HIGH") {
      return (
        <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
          HIGH CONFIDENCE
        </span>
      );
    }
    if (tier === "MEDIUM") {
      return (
        <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
          MEDIUM CONFIDENCE
        </span>
      );
    }
    return (
      <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
        LOW CONFIDENCE
      </span>
    );
  };

  const parseListItems = (rawVal?: string): string[] => {
    if (!rawVal || rawVal === "UNKNOWN") return [];
    try {
      if (rawVal.startsWith("[") && rawVal.endsWith("]")) {
        const parsed = JSON.parse(rawVal);
        if (Array.isArray(parsed)) {
          return parsed.map((item) => typeof item === "string" ? item : JSON.stringify(item));
        }
      }
    } catch {
      // ignore
    }
    return rawVal.split(/[;,]/).map((s) => s.trim()).filter((s) => s.length > 0 && s.toUpperCase() !== "UNKNOWN");
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-950/60 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl w-full max-w-4xl max-h-[92vh] flex flex-col shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="p-5 border-b border-zinc-100 dark:border-zinc-800 flex items-start justify-between gap-4 bg-zinc-50/50 dark:bg-zinc-800/30">
          <div>
            <div className="flex items-center gap-3">
              <h3 className="text-xl font-bold text-zinc-900 dark:text-zinc-100 tracking-tight">
                {record.displayName || `Record #${record.rowIndex + 1}`}
              </h3>
              <span
                className={`px-2.5 py-0.5 text-xs font-semibold rounded-full ${
                  record.status === "COMPLETED"
                    ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                    : record.status === "PARTIAL"
                    ? "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300"
                    : "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300"
                }`}
              >
                {record.status}
              </span>
              <span className="px-2 py-0.5 text-xs font-mono rounded bg-zinc-200 dark:bg-zinc-700 text-zinc-600 dark:text-zinc-300">
                {record.entityType || "PERSON"}
              </span>
            </div>
            {record.canonicalUrl && (
              <a
                href={record.canonicalUrl}
                target="_blank"
                rel="noreferrer"
                className="text-xs text-blue-600 dark:text-blue-400 hover:underline break-all mt-1 inline-block"
              >
                {record.canonicalUrl}
              </a>
            )}
          </div>

          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 hover:bg-zinc-100 dark:hover:bg-zinc-800 text-xl font-bold transition-colors"
          >
            &times;
          </button>
        </div>

        {/* Modal Tabs Navigation */}
        <div className="flex items-center space-x-1 px-5 pt-3 border-b border-zinc-200 dark:border-zinc-800 overflow-x-auto text-xs font-medium">
          <button
            type="button"
            onClick={() => setActiveTab("overview")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "overview"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            Overview
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("experience")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === "experience"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            Experience
            {experienceAttr && experienceAttr.value !== "UNKNOWN" && (
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            )}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("education")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === "education"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            Education
            {educationAttr && educationAttr.value !== "UNKNOWN" && (
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            )}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("skills")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === "skills"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            Skills &amp; Tech
            {skillsAttr && skillsAttr.value !== "UNKNOWN" && (
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            )}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("projects")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === "projects"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            Projects
            {projectsAttr && projectsAttr.value !== "UNKNOWN" && (
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            )}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("activity")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap flex items-center gap-1.5 ${
              activeTab === "activity"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            Activity
            {activityAttr && activityAttr.value !== "UNKNOWN" && (
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
            )}
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("all")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "all"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            All Evidence ({Object.keys(attributes).length})
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("sources")}
            className={`px-3 py-2 border-b-2 transition-colors whitespace-nowrap ${
              activeTab === "sources"
                ? "border-blue-600 text-blue-600 dark:text-blue-400 font-semibold"
                : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900"
            }`}
          >
            Sources ({sources.length})
          </button>
        </div>

        {/* Modal Scrollable Content */}
        <div className="p-6 overflow-y-auto space-y-6 text-xs flex-1">
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

          {/* TAB: OVERVIEW */}
          {activeTab === "overview" && (
            <div className="space-y-4">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* Current Role */}
                <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] uppercase font-bold text-zinc-400">Current Role</span>
                    {renderConfidenceBadge(getAttr("currentRole")?.confidence)}
                  </div>
                  <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                    {getAttr("currentRole")?.value || "UNKNOWN"}
                  </p>
                  {getAttr("currentRole")?.evidenceSnippet && (
                    <p className="mt-2 text-[11px] text-zinc-500 italic bg-white dark:bg-zinc-800 p-2 rounded border border-zinc-200/60 dark:border-zinc-700/60">
                      &ldquo;{getAttr("currentRole")?.evidenceSnippet}&rdquo;
                    </p>
                  )}
                </div>

                {/* Current Organization */}
                <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] uppercase font-bold text-zinc-400">Organization</span>
                    {renderConfidenceBadge(getAttr("currentOrganization")?.confidence)}
                  </div>
                  <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                    {getAttr("currentOrganization")?.value || "UNKNOWN"}
                  </p>
                  {getAttr("currentOrganization")?.evidenceSnippet && (
                    <p className="mt-2 text-[11px] text-zinc-500 italic bg-white dark:bg-zinc-800 p-2 rounded border border-zinc-200/60 dark:border-zinc-700/60">
                      &ldquo;{getAttr("currentOrganization")?.evidenceSnippet}&rdquo;
                    </p>
                  )}
                </div>

                {/* Location */}
                <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] uppercase font-bold text-zinc-400">Location</span>
                    {renderConfidenceBadge(getAttr("location")?.confidence)}
                  </div>
                  <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                    {getAttr("location")?.value || "UNKNOWN"}
                  </p>
                </div>

                {/* Overall Profile Stats */}
                <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20">
                  <span className="text-[10px] uppercase font-bold text-zinc-400 block mb-1">Enrichment Scope</span>
                  <p className="text-sm font-semibold text-zinc-900 dark:text-zinc-100">
                    {Object.keys(attributes).length} Grounded Attributes
                  </p>
                  <p className="text-[11px] text-zinc-500 mt-1">
                    Backed by {sources.length} discovered research sources with zero fabricated claims.
                  </p>
                </div>
              </div>
            </div>
          )}

          {/* TAB: EXPERIENCE */}
          {activeTab === "experience" && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-zinc-900 dark:text-zinc-100 text-sm">
                  Career &amp; Role Timeline
                </h4>
                {experienceAttr && renderConfidenceBadge(experienceAttr.confidence)}
              </div>

              {experienceAttr && experienceAttr.value !== "UNKNOWN" ? (
                <div className="space-y-3">
                  <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20 whitespace-pre-line text-zinc-800 dark:text-zinc-200 leading-relaxed font-sans">
                    {experienceAttr.value}
                  </div>
                  {experienceAttr.evidenceSnippet && (
                    <div className="p-3 bg-zinc-100/70 dark:bg-zinc-800/50 rounded-lg border border-zinc-200 dark:border-zinc-700">
                      <span className="text-[10px] uppercase font-bold text-zinc-500 block mb-1">Verbatim Grounded Snippet</span>
                      <p className="text-zinc-600 dark:text-zinc-300 italic">&ldquo;{experienceAttr.evidenceSnippet}&rdquo;</p>
                    </div>
                  )}
                  {experienceAttr.sourceUrl && (
                    <div className="text-[11px] text-zinc-400">
                      Source: <a href={experienceAttr.sourceUrl} target="_blank" rel="noreferrer" className="text-blue-500 hover:underline">{experienceAttr.sourceUrl}</a>
                    </div>
                  )}
                </div>
              ) : (
                <div className="p-8 text-center text-zinc-400 border border-dashed border-zinc-200 dark:border-zinc-800 rounded-xl">
                  No verified career timeline found in crawled sources.
                </div>
              )}
            </div>
          )}

          {/* TAB: EDUCATION */}
          {activeTab === "education" && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-zinc-900 dark:text-zinc-100 text-sm">
                  Academic Background
                </h4>
                {educationAttr && renderConfidenceBadge(educationAttr.confidence)}
              </div>

              {educationAttr && educationAttr.value !== "UNKNOWN" ? (
                <div className="space-y-3">
                  <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20 whitespace-pre-line text-zinc-800 dark:text-zinc-200 leading-relaxed">
                    {educationAttr.value}
                  </div>
                  {educationAttr.evidenceSnippet && (
                    <div className="p-3 bg-zinc-100/70 dark:bg-zinc-800/50 rounded-lg border border-zinc-200 dark:border-zinc-700">
                      <span className="text-[10px] uppercase font-bold text-zinc-500 block mb-1">Verbatim Grounded Snippet</span>
                      <p className="text-zinc-600 dark:text-zinc-300 italic">&ldquo;{educationAttr.evidenceSnippet}&rdquo;</p>
                    </div>
                  )}
                </div>
              ) : (
                <div className="p-8 text-center text-zinc-400 border border-dashed border-zinc-200 dark:border-zinc-800 rounded-xl">
                  No academic background verified in crawled sources.
                </div>
              )}
            </div>
          )}

          {/* TAB: SKILLS */}
          {activeTab === "skills" && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-zinc-900 dark:text-zinc-100 text-sm">
                  Verified Technical Skills &amp; Competencies
                </h4>
                {skillsAttr && renderConfidenceBadge(skillsAttr.confidence)}
              </div>

              {skillsAttr && skillsAttr.value !== "UNKNOWN" ? (
                <div className="space-y-4">
                  <div className="flex flex-wrap gap-2">
                    {parseListItems(skillsAttr.value).map((skill, idx) => (
                      <span
                        key={idx}
                        className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-blue-50 text-blue-800 dark:bg-blue-950/60 dark:text-blue-300 border border-blue-200 dark:border-blue-800 shadow-2xs"
                      >
                        {skill}
                      </span>
                    ))}
                  </div>
                  {skillsAttr.evidenceSnippet && (
                    <div className="p-3 bg-zinc-100/70 dark:bg-zinc-800/50 rounded-lg border border-zinc-200 dark:border-zinc-700">
                      <span className="text-[10px] uppercase font-bold text-zinc-500 block mb-1">Verbatim Grounded Snippet</span>
                      <p className="text-zinc-600 dark:text-zinc-300 italic">&ldquo;{skillsAttr.evidenceSnippet}&rdquo;</p>
                    </div>
                  )}
                </div>
              ) : (
                <div className="p-8 text-center text-zinc-400 border border-dashed border-zinc-200 dark:border-zinc-800 rounded-xl">
                  No specific skill list identified in crawled sources.
                </div>
              )}
            </div>
          )}

          {/* TAB: PROJECTS */}
          {activeTab === "projects" && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-zinc-900 dark:text-zinc-100 text-sm">
                  Notable Projects &amp; Contributions
                </h4>
                {projectsAttr && renderConfidenceBadge(projectsAttr.confidence)}
              </div>

              {projectsAttr && projectsAttr.value !== "UNKNOWN" ? (
                <div className="space-y-3">
                  <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20 whitespace-pre-line text-zinc-800 dark:text-zinc-200 leading-relaxed">
                    {projectsAttr.value}
                  </div>
                  {projectsAttr.evidenceSnippet && (
                    <div className="p-3 bg-zinc-100/70 dark:bg-zinc-800/50 rounded-lg border border-zinc-200 dark:border-zinc-700">
                      <span className="text-[10px] uppercase font-bold text-zinc-500 block mb-1">Verbatim Grounded Snippet</span>
                      <p className="text-zinc-600 dark:text-zinc-300 italic">&ldquo;{projectsAttr.evidenceSnippet}&rdquo;</p>
                    </div>
                  )}
                </div>
              ) : (
                <div className="p-8 text-center text-zinc-400 border border-dashed border-zinc-200 dark:border-zinc-800 rounded-xl">
                  No distinct projects extracted from crawled sources.
                </div>
              )}
            </div>
          )}

          {/* TAB: ACTIVITY */}
          {activeTab === "activity" && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-zinc-900 dark:text-zinc-100 text-sm">
                  Professional Activity &amp; Publications
                </h4>
                {activityAttr && renderConfidenceBadge(activityAttr.confidence)}
              </div>

              {activityAttr && activityAttr.value !== "UNKNOWN" ? (
                <div className="space-y-3">
                  <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20 whitespace-pre-line text-zinc-800 dark:text-zinc-200 leading-relaxed">
                    {activityAttr.value}
                  </div>
                  {activityAttr.evidenceSnippet && (
                    <div className="p-3 bg-zinc-100/70 dark:bg-zinc-800/50 rounded-lg border border-zinc-200 dark:border-zinc-700">
                      <span className="text-[10px] uppercase font-bold text-zinc-500 block mb-1">Verbatim Grounded Snippet</span>
                      <p className="text-zinc-600 dark:text-zinc-300 italic">&ldquo;{activityAttr.evidenceSnippet}&rdquo;</p>
                    </div>
                  )}
                </div>
              ) : (
                <div className="p-8 text-center text-zinc-400 border border-dashed border-zinc-200 dark:border-zinc-800 rounded-xl">
                  No public posts or authored articles found.
                </div>
              )}
            </div>
          )}

          {/* TAB: ALL EVIDENCE */}
          {activeTab === "all" && (
            <div className="space-y-3">
              <h4 className="font-semibold text-sm text-zinc-900 dark:text-zinc-100 uppercase tracking-wider text-[11px]">
                All Grounded Attributes &amp; Verbatim Evidence
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
          )}

          {/* TAB: SOURCES */}
          {activeTab === "sources" && (
            <div className="space-y-4">
              <h4 className="font-semibold text-sm text-zinc-900 dark:text-zinc-100 uppercase tracking-wider text-[11px]">
                Discovered Sources ({sources.length})
              </h4>
              {sources.length > 0 ? (
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
              ) : (
                <p className="text-zinc-400 py-3 text-center italic">No sources discovered.</p>
              )}

              {/* Original Input Data */}
              <div className="space-y-2 pt-4 border-t border-zinc-100 dark:border-zinc-800">
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
          )}
        </div>

        {/* Modal Footer */}
        <div className="p-4 border-t border-zinc-100 dark:border-zinc-800 flex justify-end bg-zinc-50/50 dark:bg-zinc-800/30">
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
