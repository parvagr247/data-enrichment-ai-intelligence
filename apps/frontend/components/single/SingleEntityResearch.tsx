"use client";

import React, { useState, useRef, useEffect, useCallback } from "react";
import {
  EntityType,
  ResearchRequest,
  ResearchResponse,
  ResearchJobResponse,
  ConfidenceTier,
} from "@/types/research";
import { researchService } from "@/services/researchService";

interface SingleEntityResearchProps {
  onEntityResearched?: () => void;
}

export function SingleEntityResearch({ onEntityResearched }: SingleEntityResearchProps) {
  const [url, setUrl] = useState("https://github.com/spring-projects/spring-boot");
  const [name, setName] = useState("Spring Boot");
  const [entityType, setEntityType] = useState<EntityType>("REPOSITORY");
  const [singleRequirement, setSingleRequirement] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [singleErrorMessage, setSingleErrorMessage] = useState<string | null>(null);
  const [currentJob, setCurrentJob] = useState<ResearchJobResponse | null>(null);
  const [researchResult, setResearchResult] = useState<ResearchResponse | null>(null);
  const pollingRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, []);

  const pollJobStatus = useCallback(
    (jobId: string) => {
      if (pollingRef.current) clearInterval(pollingRef.current);

      pollingRef.current = setInterval(async () => {
        try {
          const job = await researchService.getJob(jobId);
          setCurrentJob(job);

          if (job.status === "COMPLETED") {
            if (pollingRef.current) clearInterval(pollingRef.current);
            setIsSubmitting(false);
            if (job.result) {
              setResearchResult(job.result);
            }
            onEntityResearched?.();
          } else if (job.status === "FAILED") {
            if (pollingRef.current) clearInterval(pollingRef.current);
            setIsSubmitting(false);
            setSingleErrorMessage(job.error || "Research job encountered an execution failure");
          }
        } catch (err: unknown) {
          if (pollingRef.current) clearInterval(pollingRef.current);
          setIsSubmitting(false);
          setSingleErrorMessage(err instanceof Error ? err.message : "Error polling job status");
        }
      }, 1000);
    },
    [onEntityResearched]
  );

  const handleStartResearch = async (asyncMode: boolean) => {
    setSingleErrorMessage(null);
    setResearchResult(null);
    setCurrentJob(null);
    setIsSubmitting(true);

    const payload: ResearchRequest = {
      url: url.trim(),
      name: name.trim() || undefined,
      entityType,
      userRequirement: singleRequirement.trim() || undefined,
    };

    try {
      if (asyncMode) {
        const job = await researchService.submitJob(payload);
        setCurrentJob(job);
        pollJobStatus(job.jobId);
      } else {
        const data = await researchService.executeResearch(payload);
        setResearchResult(data);
        setIsSubmitting(false);
        onEntityResearched?.();
      }
    } catch (err: unknown) {
      setIsSubmitting(false);
      setSingleErrorMessage(err instanceof Error ? err.message : "Research request failed");
    }
  };

  const renderConfidenceBadge = (confidence?: ConfidenceTier | string) => {
    const tier = (confidence || "LOW").toUpperCase();
    if (tier === "HIGH") {
      return (
        <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
          HIGH
        </span>
      );
    }
    if (tier === "MEDIUM") {
      return (
        <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
          MEDIUM
        </span>
      );
    }
    return (
      <span className="px-2 py-0.5 text-[10px] font-semibold rounded-full bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
        LOW
      </span>
    );
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Form Left Column */}
      <div className="lg:col-span-1 space-y-4 bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs h-fit">
        <div>
          <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            Single Entity Research
          </h2>
          <p className="text-xs text-zinc-500 mt-0.5">
            Test direct discovery and fact extraction for an individual seed.
          </p>
        </div>

        {singleErrorMessage && (
          <div className="p-3 rounded-lg bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-900 text-rose-800 dark:text-rose-200 text-xs">
            {singleErrorMessage}
          </div>
        )}

        <div className="space-y-3 text-xs">
          <div>
            <label className="block font-medium text-zinc-600 dark:text-zinc-400 mb-1">
              Canonical / Seed URL *
            </label>
            <input
              type="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://github.com/spring-projects/spring-boot"
              required
              className="w-full px-3 py-2 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-transparent text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
            />
          </div>

          <div>
            <label className="block font-medium text-zinc-600 dark:text-zinc-400 mb-1">
              Display Name (Optional Hint)
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Spring Boot"
              className="w-full px-3 py-2 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-transparent text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
            />
          </div>

          <div>
            <label className="block font-medium text-zinc-600 dark:text-zinc-400 mb-1">
              Entity Type
            </label>
            <select
              value={entityType}
              onChange={(e) => setEntityType(e.target.value as EntityType)}
              className="w-full px-3 py-2 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
            >
              <option value="REPOSITORY">REPOSITORY</option>
              <option value="ORGANIZATION">ORGANIZATION</option>
              <option value="PERSON">PERSON</option>
              <option value="PRODUCT">PRODUCT</option>
              <option value="WEBSITE">WEBSITE</option>
              <option value="OTHER">OTHER</option>
            </select>
          </div>

          <div>
            <label className="block font-medium text-zinc-600 dark:text-zinc-400 mb-1">
              Custom Requirement (Optional)
            </label>
            <input
              type="text"
              value={singleRequirement}
              onChange={(e) => setSingleRequirement(e.target.value)}
              placeholder="e.g. Find founders, tech stack, and primary language"
              className="w-full px-3 py-2 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-transparent text-zinc-900 dark:text-zinc-100 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100"
            />
          </div>
        </div>

        <div className="pt-2 space-y-2">
          <button
            type="button"
            disabled={isSubmitting || !url}
            onClick={() => handleStartResearch(true)}
            className="w-full py-2.5 px-4 rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 font-semibold text-xs hover:opacity-90 disabled:opacity-50 transition-all flex justify-center items-center gap-2"
          >
            {isSubmitting ? (
              <>
                <span className="animate-spin inline-block w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full" />
                <span>Researching...</span>
              </>
            ) : (
              <span>Start Async Research Job</span>
            )}
          </button>

          <button
            type="button"
            disabled={isSubmitting || !url}
            onClick={() => handleStartResearch(false)}
            className="w-full py-2 px-4 rounded-lg border border-zinc-300 dark:border-zinc-700 text-zinc-700 dark:text-zinc-300 text-xs hover:bg-zinc-50 dark:hover:bg-zinc-800 disabled:opacity-50 transition-colors"
          >
            Direct Synchronous Run
          </button>
        </div>

        {/* Quick Seeds */}
        <div className="pt-3 border-t border-zinc-100 dark:border-zinc-800 text-xs text-zinc-500 space-y-1.5">
          <span className="font-semibold uppercase text-[10px] tracking-wider text-zinc-400 block">
            Quick Seeds:
          </span>
          <button
            type="button"
            onClick={() => {
              setUrl("https://github.com/spring-projects/spring-boot");
              setName("Spring Boot");
              setEntityType("REPOSITORY");
            }}
            className="block text-left text-zinc-600 dark:text-zinc-400 hover:underline text-[11px]"
          >
            &bull; Spring Boot (Repository)
          </button>
          <button
            type="button"
            onClick={() => {
              setUrl("https://spring.io");
              setName("Spring");
              setEntityType("ORGANIZATION");
            }}
            className="block text-left text-zinc-600 dark:text-zinc-400 hover:underline text-[11px]"
          >
            &bull; Spring.io (Organization)
          </button>
        </div>
      </div>

      {/* Results Right Column */}
      <div className="lg:col-span-2 space-y-6">
        {/* Job Tracker */}
        {currentJob && (
          <div className="bg-white dark:bg-zinc-900 p-5 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-[10px] font-semibold uppercase tracking-wider text-zinc-400">
                  Background Research Job
                </span>
                <div className="font-mono text-xs text-zinc-700 dark:text-zinc-300">{currentJob.jobId}</div>
              </div>
              <span
                className={`px-2.5 py-1 text-xs font-semibold rounded-full ${
                  currentJob.status === "COMPLETED"
                    ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300"
                    : currentJob.status === "FAILED"
                    ? "bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300"
                    : "bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300"
                }`}
              >
                {currentJob.status}
              </span>
            </div>

            <div className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-full h-2 overflow-hidden">
              <div
                className="bg-blue-600 dark:bg-blue-500 h-2 transition-all duration-300"
                style={{ width: `${currentJob.progress}%` }}
              />
            </div>

            <div className="flex justify-between text-xs text-zinc-500 font-mono text-[11px]">
              <span>Progress: {currentJob.progress}%</span>
              <span>{currentJob.durationMs != null ? `${currentJob.durationMs}ms` : "Processing..."}</span>
            </div>
          </div>
        )}

        {/* Research Result */}
        {researchResult && (
          <div className="space-y-5">
            {/* Entity Summary */}
            <div className="bg-white dark:bg-zinc-900 p-5 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs space-y-2">
              <div className="flex justify-between items-start gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-lg font-bold text-zinc-900 dark:text-zinc-100">
                      {researchResult.result.displayName}
                    </h3>
                    <span className="px-2 py-0.5 text-xs font-semibold rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                      {researchResult.result.entityType}
                    </span>
                  </div>
                  <a
                    href={researchResult.result.canonicalUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="text-xs text-blue-600 dark:text-blue-400 hover:underline break-all block mt-0.5"
                  >
                    {researchResult.result.canonicalUrl}
                  </a>
                </div>
                <div className="text-right text-xs text-zinc-400 font-mono">
                  <div>{researchResult.executionTimeMs}ms</div>
                  <div className="text-[10px] truncate max-w-[150px]">{researchResult.entityId}</div>
                </div>
              </div>

              {researchResult.warnings && researchResult.warnings.length > 0 && (
                <div className="p-3 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded-lg text-xs text-amber-800 dark:text-amber-200 space-y-0.5 mt-2">
                  <span className="font-semibold">Diagnostics:</span>
                  <ul className="list-disc list-inside">
                    {researchResult.warnings.map((w, idx) => (
                      <li key={idx}>{w}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>

            {/* Extracted Attributes */}
            <div className="bg-white dark:bg-zinc-900 p-5 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs space-y-3">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
                Extracted Attributes &amp; Evidence ({Object.keys(researchResult.result.attributes).length})
              </h4>
              <div className="divide-y divide-zinc-100 dark:divide-zinc-800 text-xs">
                {Object.entries(researchResult.result.attributes).map(([attrKey, tuple]) => (
                  <div key={attrKey} className="py-2.5 space-y-1">
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-zinc-800 dark:text-zinc-200 capitalize">
                        {attrKey.replace(/_/g, " ")}
                      </span>
                      <div className="flex items-center gap-1.5">
                        {tuple.conflictDetected && (
                          <span className="px-1.5 py-0.2 text-[9px] font-semibold rounded bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
                            Conflict
                          </span>
                        )}
                        {tuple.corroboratingSources && tuple.corroboratingSources.length > 1 && (
                          <span className="px-1.5 py-0.2 text-[9px] font-semibold rounded bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
                            +{tuple.corroboratingSources.length} sources
                          </span>
                        )}
                        {renderConfidenceBadge(tuple.confidence)}
                      </div>
                    </div>
                    <div className="p-2 bg-zinc-50 dark:bg-zinc-800/50 rounded text-zinc-900 dark:text-zinc-100 font-medium">
                      {tuple.value}
                    </div>
                    {tuple.evidenceSnippet && (
                      <div className="text-[11px] text-zinc-500 italic">
                        &ldquo;{tuple.evidenceSnippet}&rdquo;
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* Discovered Sources */}
            <div className="bg-white dark:bg-zinc-900 p-5 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-xs space-y-3">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">
                Discovered Sources ({researchResult.sources.length})
              </h4>
              <div className="space-y-2 text-xs">
                {researchResult.sources.map((src, i) => (
                  <div
                    key={i}
                    className="p-2.5 rounded-lg border border-zinc-200/80 dark:border-zinc-800 bg-zinc-50/40 dark:bg-zinc-800/20 flex flex-col sm:flex-row sm:items-center justify-between gap-1"
                  >
                    <div className="min-w-0">
                      <span className="font-semibold text-zinc-800 dark:text-zinc-200">
                        {src.title || src.url}
                      </span>
                      <a
                        href={src.url}
                        target="_blank"
                        rel="noreferrer"
                        className="text-blue-500 hover:underline truncate block text-[11px]"
                      >
                        {src.url}
                      </a>
                    </div>
                    <span className="text-[10px] font-mono text-zinc-400 shrink-0">
                      {src.sourceType}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Empty State */}
        {!currentJob && !researchResult && (
          <div className="bg-white dark:bg-zinc-900 p-12 rounded-xl border border-zinc-200 dark:border-zinc-800 text-center text-zinc-400 space-y-2">
            <div className="text-3xl">&infin;</div>
            <div className="text-sm font-medium">Ready to Research</div>
            <p className="text-xs text-zinc-500 max-w-sm mx-auto">
              Submit an entity seed on the left to trigger search discovery, content fetching, and grounded attribute extraction.
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
