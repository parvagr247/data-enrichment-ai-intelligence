"use client";

import React, { useState, useEffect } from "react";
import {
  EnrichedRecord,
  ResearchProfile,
  ObjectiveAssessment,
  RecommendedApproach,
  ResearchFinding,
  PriorityTier,
  ApproachType,
} from "@/types/dataset";

interface ResearchProfileModalProps {
  record: EnrichedRecord | null;
  onClose: () => void;
}

type ModalTab =
  | "summary"
  | "assessment"
  | "approach"
  | "activity"
  | "evidence"
  | "raw";

export function ResearchProfileModal({ record, onClose }: ResearchProfileModalProps) {
  const [activeTab, setActiveTab] = useState<ModalTab>("summary");
  const [copiedPointIndex, setCopiedPointIndex] = useState<number | null>(null);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  if (!record) return null;

  // Extract or synthesize fallback profile data
  const profile: ResearchProfile = record.profile || {
    currentRole: record.attributes?.currentRole?.value || "UNKNOWN",
    currentOrganization: record.attributes?.currentOrganization?.value || "UNKNOWN",
    location: record.attributes?.location?.value || "UNKNOWN",
    professionalSummary:
      record.attributes?.summary?.value ||
      record.attributes?.description?.value ||
      `${record.displayName || "Entity"} profile identified during enrichment.`,
    careerBackground: "Career background compiled from public professional records.",
    technicalExpertise: record.attributes?.skills?.value
      ? record.attributes.skills.value.split(",").map((s) => s.trim())
      : [],
    relevantExperience: [],
    relevantProjects: [],
    publicActivity: [],
  };

  const assessment: ObjectiveAssessment = record.assessment || {
    overallScore: Math.round((record.confidence || 0.5) * 100),
    priorityTier: (record.confidence && record.confidence > 0.7 ? "HIGH" : record.confidence && record.confidence > 0.4 ? "MEDIUM" : "LOW") as PriorityTier,
    whyRelevant: "Identified during enrichment based on matched professional criteria.",
    dimensions: {},
    keyStrengths: [],
    limitationsOrGaps: [],
  };

  const recommendation: RecommendedApproach = record.recommendation || {
    approachType: "NETWORKING_CONVERSATION" as ApproachType,
    summary: "Initiate professional networking connection referencing shared industry background.",
    rationale: "Entity exhibits verified professional experience in relevant domain.",
    suggestedTalkingPoints: [
      `Connect referencing their background at ${profile.currentOrganization !== "UNKNOWN" ? profile.currentOrganization : "their current team"}.`,
    ],
  };

  const findings: ResearchFinding[] = record.findings || [];
  const sources = record.sources || [];

  const copyTalkingPoint = (text: string, index: number) => {
    navigator.clipboard.writeText(text);
    setCopiedPointIndex(index);
    setTimeout(() => setCopiedPointIndex(null), 2000);
  };

  const renderTierBadge = (tier: PriorityTier) => {
    switch (tier) {
      case "HIGH":
        return (
          <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
            HIGH PRIORITY
          </span>
        );
      case "MEDIUM":
        return (
          <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
            MEDIUM PRIORITY
          </span>
        );
      case "LOW":
        return (
          <span className="px-2.5 py-1 text-xs font-bold rounded-full bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 border border-zinc-300 dark:border-zinc-700">
            LOW PRIORITY
          </span>
        );
      default:
        return (
          <span className="px-2.5 py-1 text-xs font-medium rounded-full bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400">
            NEUTRAL / NONE
          </span>
        );
    }
  };

  const renderApproachBadge = (type: ApproachType) => {
    const formatted = type.replace(/_/g, " ");
    return (
      <span className="px-2 py-0.5 text-xs font-semibold rounded-md bg-indigo-50 text-indigo-700 dark:bg-indigo-950 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800">
        {formatted}
      </span>
    );
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-200">
      <div
        className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl max-w-4xl w-full max-h-[92vh] flex flex-col shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        {/* MODAL HEADER */}
        <div className="p-6 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50/70 dark:bg-zinc-800/50">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-1.5 flex-1 min-w-0">
              <div className="flex items-center gap-3 flex-wrap">
                <h3 className="text-xl font-bold text-zinc-900 dark:text-zinc-100 truncate">
                  {record.displayName || "Research Profile"}
                </h3>
                {renderTierBadge(assessment.priorityTier)}
                <div className="flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300 font-mono text-xs font-semibold border border-blue-200 dark:border-blue-800">
                  <span>Score:</span>
                  <span className="text-sm font-bold">{assessment.overallScore}</span>
                  <span className="text-[10px] text-blue-400">/100</span>
                </div>
              </div>

              <div className="flex items-center gap-3 text-xs text-zinc-600 dark:text-zinc-400 flex-wrap">
                {profile.currentRole !== "UNKNOWN" && (
                  <span className="font-medium text-zinc-800 dark:text-zinc-200">
                    {profile.currentRole}
                  </span>
                )}
                {profile.currentOrganization !== "UNKNOWN" && (
                  <span>
                    @ <strong className="text-zinc-700 dark:text-zinc-300">{profile.currentOrganization}</strong>
                  </span>
                )}
                {profile.location !== "UNKNOWN" && (
                  <span className="flex items-center gap-1 text-zinc-500">
                    📍 {profile.location}
                  </span>
                )}
                {record.canonicalUrl && (
                  <a
                    href={record.canonicalUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-blue-600 hover:text-blue-700 dark:text-blue-400 hover:underline flex items-center gap-1 font-mono text-[11px]"
                  >
                    🔗 Source Profile ↗
                  </a>
                )}
              </div>
            </div>

            <button
              type="button"
              onClick={onClose}
              className="p-1.5 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 rounded-lg hover:bg-zinc-200 dark:hover:bg-zinc-800 transition-colors text-lg font-bold"
              aria-label="Close modal"
            >
              ✕
            </button>
          </div>

          {/* TAB NAVIGATION */}
          <div className="flex items-center gap-1 mt-6 border-b border-zinc-200 dark:border-zinc-800 overflow-x-auto text-xs font-medium pb-px">
            <button
              type="button"
              onClick={() => setActiveTab("summary")}
              className={`px-3.5 py-2 rounded-t-lg transition-all border-b-2 whitespace-nowrap ${
                activeTab === "summary"
                  ? "border-blue-600 text-blue-600 dark:text-blue-400 bg-white dark:bg-zinc-900 font-semibold"
                  : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              Executive Summary & Background
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("assessment")}
              className={`px-3.5 py-2 rounded-t-lg transition-all border-b-2 whitespace-nowrap ${
                activeTab === "assessment"
                  ? "border-blue-600 text-blue-600 dark:text-blue-400 bg-white dark:bg-zinc-900 font-semibold"
                  : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              Objective Assessment & Fit
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("approach")}
              className={`px-3.5 py-2 rounded-t-lg transition-all border-b-2 whitespace-nowrap ${
                activeTab === "approach"
                  ? "border-blue-600 text-blue-600 dark:text-blue-400 bg-white dark:bg-zinc-900 font-semibold"
                  : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              Recommended Approach
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("activity")}
              className={`px-3.5 py-2 rounded-t-lg transition-all border-b-2 whitespace-nowrap flex items-center gap-1.5 ${
                activeTab === "activity"
                  ? "border-blue-600 text-blue-600 dark:text-blue-400 bg-white dark:bg-zinc-900 font-semibold"
                  : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              <span>Public Activity & Posts</span>
              {profile.publicActivity.length > 0 && (
                <span className="px-1.5 py-0.2 text-[10px] rounded-full bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300 font-mono">
                  {profile.publicActivity.length}
                </span>
              )}
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("evidence")}
              className={`px-3.5 py-2 rounded-t-lg transition-all border-b-2 whitespace-nowrap flex items-center gap-1.5 ${
                activeTab === "evidence"
                  ? "border-blue-600 text-blue-600 dark:text-blue-400 bg-white dark:bg-zinc-900 font-semibold"
                  : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              <span>Grounded Evidence</span>
              <span className="px-1.5 py-0.2 text-[10px] rounded-full bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300 font-mono">
                {findings.length || sources.length}
              </span>
            </button>

            <button
              type="button"
              onClick={() => setActiveTab("raw")}
              className={`px-3.5 py-2 rounded-t-lg transition-all border-b-2 whitespace-nowrap ${
                activeTab === "raw"
                  ? "border-blue-600 text-blue-600 dark:text-blue-400 bg-white dark:bg-zinc-900 font-semibold"
                  : "border-transparent text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-zinc-100"
              }`}
            >
              Raw Attributes
            </button>
          </div>
        </div>

        {/* MODAL BODY */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1 text-sm">
          {/* TAB 1: EXECUTIVE SUMMARY & BACKGROUND */}
          {activeTab === "summary" && (
            <div className="space-y-6">
              {/* Professional Summary Box */}
              <div className="bg-blue-50/40 dark:bg-blue-950/20 border border-blue-100 dark:border-blue-900/40 rounded-xl p-4.5 space-y-2">
                <h4 className="text-xs font-bold uppercase tracking-wider text-blue-900 dark:text-blue-200">
                  Professional Executive Summary
                </h4>
                <p className="text-sm text-zinc-800 dark:text-zinc-200 leading-relaxed">
                  {profile.professionalSummary}
                </p>
              </div>

              {/* Career Trajectory */}
              <div className="space-y-2">
                <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                  Career Trajectory & Context
                </h4>
                <p className="text-sm text-zinc-700 dark:text-zinc-300 leading-relaxed bg-zinc-50 dark:bg-zinc-800/40 p-3.5 rounded-lg border border-zinc-100 dark:border-zinc-800">
                  {profile.careerBackground}
                </p>
              </div>

              {/* Technical Expertise */}
              {profile.technicalExpertise.length > 0 && (
                <div className="space-y-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                    Core Technical Expertise & Competencies
                  </h4>
                  <div className="flex flex-wrap gap-2">
                    {profile.technicalExpertise.map((tech, i) => (
                      <span
                        key={i}
                        className="px-2.5 py-1 text-xs font-medium rounded-md bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200 border border-zinc-200 dark:border-zinc-700"
                      >
                        {tech}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Relevant Experience Items */}
              {profile.relevantExperience.length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                    Verified Professional Experience
                  </h4>
                  <div className="space-y-3">
                    {profile.relevantExperience.map((exp, i) => (
                      <div
                        key={i}
                        className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 space-y-2"
                      >
                        <div className="flex items-center justify-between">
                          <div className="font-semibold text-zinc-900 dark:text-zinc-100">
                            {exp.role}{" "}
                            {exp.organization && (
                              <span className="text-zinc-500 font-normal">@ {exp.organization}</span>
                            )}
                          </div>
                          {exp.duration && (
                            <span className="text-xs font-mono text-zinc-400">{exp.duration}</span>
                          )}
                        </div>
                        <p className="text-xs text-zinc-600 dark:text-zinc-300 leading-relaxed">
                          {exp.summary}
                        </p>
                        {exp.evidenceQuote && (
                          <div className="text-[11px] italic text-zinc-500 dark:text-zinc-400 bg-zinc-50 dark:bg-zinc-800/60 p-2 rounded border-l-2 border-blue-500">
                            &quot;{exp.evidenceQuote}&quot;
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: OBJECTIVE ASSESSMENT & FIT */}
          {activeTab === "assessment" && (
            <div className="space-y-6">
              {/* Why Relevant Section */}
              <div className="bg-emerald-50/40 dark:bg-emerald-950/20 border border-emerald-100 dark:border-emerald-900/40 rounded-xl p-4.5 space-y-2">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-emerald-900 dark:text-emerald-200">
                    Why Relevant to Your Research Objective
                  </h4>
                  {renderTierBadge(assessment.priorityTier)}
                </div>
                <p className="text-sm text-zinc-800 dark:text-zinc-200 leading-relaxed">
                  {assessment.whyRelevant}
                </p>
              </div>

              {/* Multi-Dimensional Scoring Cards */}
              {Object.keys(assessment.dimensions).length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                    Multi-Dimensional Fit Assessment (0–100)
                  </h4>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {Object.entries(assessment.dimensions).map(([dimKey, dim]) => (
                      <div
                        key={dimKey}
                        className="p-3.5 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-zinc-50/60 dark:bg-zinc-800/40 space-y-2"
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold capitalize text-zinc-800 dark:text-zinc-200">
                            {dimKey.replace(/([A-Z])/g, " $1")}
                          </span>
                          <span className="font-mono text-xs font-bold text-blue-600 dark:text-blue-400">
                            {dim.score}/100
                          </span>
                        </div>
                        <div className="w-full bg-zinc-200 dark:bg-zinc-700 h-1.5 rounded-full overflow-hidden">
                          <div
                            className="bg-blue-600 dark:bg-blue-500 h-full rounded-full transition-all"
                            style={{ width: `${Math.min(100, Math.max(0, dim.score))}%` }}
                          />
                        </div>
                        <p className="text-[11px] text-zinc-500 dark:text-zinc-400 leading-normal">
                          {dim.rationale}
                        </p>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Key Strengths & Gaps */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 space-y-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-400">
                    Key Strengths & Synergies
                  </h4>
                  {assessment.keyStrengths.length > 0 ? (
                    <ul className="space-y-1.5 text-xs text-zinc-700 dark:text-zinc-300">
                      {assessment.keyStrengths.map((str, i) => (
                        <li key={i} className="flex items-start gap-2">
                          <span className="text-emerald-500 font-bold">✓</span>
                          <span>{str}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-zinc-400 italic">No specific strengths recorded.</p>
                  )}
                </div>

                <div className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 space-y-2">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-amber-700 dark:text-amber-400">
                    Limitations & Potential Gaps
                  </h4>
                  {assessment.limitationsOrGaps.length > 0 ? (
                    <ul className="space-y-1.5 text-xs text-zinc-700 dark:text-zinc-300">
                      {assessment.limitationsOrGaps.map((gap, i) => (
                        <li key={i} className="flex items-start gap-2">
                          <span className="text-amber-500 font-bold">⚠</span>
                          <span>{gap}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-xs text-zinc-400 italic">No notable gaps detected.</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: RECOMMENDED APPROACH */}
          {activeTab === "approach" && (
            <div className="space-y-6">
              {/* Strategy Header Box */}
              <div className="p-4.5 rounded-xl border border-indigo-100 dark:border-indigo-900/40 bg-indigo-50/40 dark:bg-indigo-950/20 space-y-3">
                <div className="flex items-center justify-between flex-wrap gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold uppercase tracking-wider text-indigo-900 dark:text-indigo-200">
                      Engagement Strategy:
                    </span>
                    {renderApproachBadge(recommendation.approachType)}
                  </div>
                </div>
                <p className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                  {recommendation.summary}
                </p>
                <p className="text-xs text-zinc-600 dark:text-zinc-300 leading-relaxed">
                  <strong>Rationale:</strong> {recommendation.rationale}
                </p>
              </div>

              {/* Actionable Talking Points */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                  Suggested Outreach Talking Points & Angles
                </h4>
                {recommendation.suggestedTalkingPoints.length > 0 ? (
                  <div className="space-y-2.5">
                    {recommendation.suggestedTalkingPoints.map((point, i) => (
                      <div
                        key={i}
                        className="p-3.5 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 flex items-start justify-between gap-3 group hover:border-blue-300 dark:hover:border-blue-700 transition-colors"
                      >
                        <div className="flex items-start gap-2.5 text-xs text-zinc-800 dark:text-zinc-200 leading-relaxed flex-1">
                          <span className="text-blue-600 font-bold">💬</span>
                          <span>{point}</span>
                        </div>
                        <button
                          type="button"
                          onClick={() => copyTalkingPoint(point, i)}
                          className="px-2 py-1 text-[11px] font-medium rounded bg-zinc-100 dark:bg-zinc-800 hover:bg-zinc-200 dark:hover:bg-zinc-700 text-zinc-600 dark:text-zinc-300 transition-colors shrink-0"
                        >
                          {copiedPointIndex === i ? "Copied! ✓" : "Copy"}
                        </button>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-zinc-400 italic">No specific talking points generated.</p>
                )}
              </div>
            </div>
          )}

          {/* TAB 4: PUBLIC ACTIVITY & POSTS */}
          {activeTab === "activity" && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                  LinkedIn & Public Activity Highlights
                </h4>
                <span className="text-xs text-zinc-400 font-mono">
                  {profile.publicActivity.length} items discovered
                </span>
              </div>

              {profile.publicActivity.length > 0 ? (
                <div className="space-y-3">
                  {profile.publicActivity.map((act, i) => (
                    <div
                      key={i}
                      className="p-4 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 space-y-2"
                    >
                      <div className="flex items-center justify-between flex-wrap gap-2">
                        <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300 border border-purple-200 dark:border-purple-800">
                          {act.classification.replace(/_/g, " ")}
                        </span>
                        <span className="text-[10px] font-mono text-zinc-400 uppercase">
                          {act.activityType}
                        </span>
                      </div>
                      <h5 className="text-xs font-semibold text-zinc-900 dark:text-zinc-100">
                        {act.title}
                      </h5>
                      <p className="text-xs text-zinc-600 dark:text-zinc-300 leading-relaxed">
                        {act.summary}
                      </p>
                      {act.sourceUrl && (
                        <a
                          href={act.sourceUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-[11px] text-blue-600 hover:underline flex items-center gap-1 font-mono pt-1"
                        >
                          View Original Post ↗
                        </a>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div className="text-center py-10 text-xs text-zinc-500 border border-dashed border-zinc-200 dark:border-zinc-800 rounded-xl">
                  No public posts or recent activity detected in the researched sources.
                </div>
              )}
            </div>
          )}

          {/* TAB 5: GROUNDED EVIDENCE & PROVENANCE */}
          {activeTab === "evidence" && (
            <div className="space-y-6">
              {/* Findings distinguishing facts vs inferences */}
              {findings.length > 0 && (
                <div className="space-y-3">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                    Evidence-Grounded Findings
                  </h4>
                  <div className="space-y-2.5">
                    {findings.map((f, i) => {
                      const isFact = f.findingType === "FACT_SOURCE_DERIVED";
                      return (
                        <div
                          key={i}
                          className="p-3.5 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 space-y-1.5"
                        >
                          <div className="flex items-center justify-between">
                            <span
                              className={`px-2 py-0.5 text-[10px] font-bold rounded-md ${
                                isFact
                                  ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                                  : "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300"
                              }`}
                            >
                              {isFact ? "FACT (SOURCE DERIVED)" : "INFERRED ASSESSMENT"}
                            </span>
                            <span className="text-[10px] font-mono text-zinc-400">
                              Confidence: {f.confidence}
                            </span>
                          </div>
                          <p className="text-xs font-medium text-zinc-900 dark:text-zinc-100">
                            {f.claim}
                          </p>
                          {f.evidenceSnippet && (
                            <p className="text-[11px] text-zinc-500 italic bg-zinc-50 dark:bg-zinc-800/40 p-2 rounded border-l-2 border-zinc-300 dark:border-zinc-700">
                              &quot;{f.evidenceSnippet}&quot;
                            </p>
                          )}
                          {f.sourceUrl && (
                            <a
                              href={f.sourceUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-[10px] text-blue-600 hover:underline font-mono truncate block"
                            >
                              {f.sourceTitle ? `${f.sourceTitle} — ` : ""}
                              {f.sourceUrl}
                            </a>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {/* Research Sources */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                  Corroborating Discovered Sources ({sources.length})
                </h4>
                <div className="space-y-2">
                  {sources.map((s, i) => (
                    <div
                      key={i}
                      className="p-3 rounded-lg border border-zinc-100 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/20 flex items-start justify-between gap-3 text-xs"
                    >
                      <div className="space-y-1 min-w-0 flex-1">
                        <a
                          href={s.url}
                          target="_blank"
                          rel="noreferrer"
                          className="font-medium text-blue-600 hover:underline truncate block"
                        >
                          {s.title || s.url}
                        </a>
                        {s.snippet && (
                          <p className="text-[11px] text-zinc-500 line-clamp-2">{s.snippet}</p>
                        )}
                        <div className="flex items-center gap-2 text-[10px] text-zinc-400 font-mono">
                          <span>{s.domain || "web"}</span>
                          <span>•</span>
                          <span>Type: {s.sourceType}</span>
                          {s.relevance != null && (
                            <>
                              <span>•</span>
                              <span>Relevance: {(s.relevance * 100).toFixed(0)}%</span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* TAB 6: RAW ATTRIBUTES GRID */}
          {activeTab === "raw" && (
            <div className="space-y-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-zinc-500">
                Extracted Attributes Grid
              </h4>
              <div className="border border-zinc-200 dark:border-zinc-800 rounded-xl overflow-hidden">
                <table className="w-full text-xs text-left">
                  <thead className="bg-zinc-50 dark:bg-zinc-800/80 border-b border-zinc-200 dark:border-zinc-800 text-zinc-500 font-semibold text-[11px]">
                    <tr>
                      <th className="p-3">Attribute</th>
                      <th className="p-3">Value</th>
                      <th className="p-3">Confidence</th>
                      <th className="p-3">Source & Evidence</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                    {record.attributes &&
                      Object.entries(record.attributes).map(([k, v]) => (
                        <tr key={k} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/50">
                          <td className="p-3 font-semibold text-zinc-800 dark:text-zinc-200 capitalize">
                            {k.replace(/([A-Z])/g, " $1")}
                          </td>
                          <td className="p-3 text-zinc-900 dark:text-zinc-100 max-w-xs break-words">
                            {v.value}
                          </td>
                          <td className="p-3">
                            <span className="px-1.5 py-0.5 text-[10px] font-semibold rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                              {v.confidence || "MEDIUM"}
                            </span>
                          </td>
                          <td className="p-3 text-[11px] text-zinc-500 max-w-xs truncate">
                            {v.sourceUrl ? (
                              <a
                                href={v.sourceUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="text-blue-600 hover:underline"
                              >
                                {v.sourceUrl}
                              </a>
                            ) : (
                              "—"
                            )}
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        {/* MODAL FOOTER */}
        <div className="p-4 border-t border-zinc-200 dark:border-zinc-800 bg-zinc-50/50 dark:bg-zinc-800/30 flex items-center justify-between text-xs text-zinc-500">
          <div>
            Entity ID: <span className="font-mono">{record.id}</span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-1.5 text-xs font-semibold rounded-lg bg-zinc-900 dark:bg-zinc-100 text-white dark:text-zinc-900 hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-colors"
          >
            Close Inspector
          </button>
        </div>
      </div>
    </div>
  );
}
