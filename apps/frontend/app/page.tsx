"use client";

import { useState, useEffect, useRef, useCallback } from "react";
import { backendConfig } from "@/config/backend";
import {
  EntityType,
  ConfidenceTier,
  ResearchRequest,
  ResearchResponse,
  ResearchJobResponse,
  EntitySummaryResponse,
  EntityDetailResponse,
} from "@/types/api";

export default function Home() {
  const [activeTab, setActiveTab] = useState<"research" | "catalog">("research");
  
  // Research Form State
  const [url, setUrl] = useState("https://github.com/spring-projects/spring-boot");
  const [name, setName] = useState("Spring Boot");
  const [entityType, setEntityType] = useState<EntityType>("REPOSITORY");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Async Job & Result State
  const [currentJob, setCurrentJob] = useState<ResearchJobResponse | null>(null);
  const [researchResult, setResearchResult] = useState<ResearchResponse | null>(null);
  const pollingRef = useRef<NodeJS.Timeout | null>(null);

  // Catalog State
  const [savedEntities, setSavedEntities] = useState<EntitySummaryResponse[]>([]);
  const [selectedEntity, setSelectedEntity] = useState<EntityDetailResponse | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  // Stop polling on unmount
  useEffect(() => {
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, []);

  const fetchCatalog = useCallback(async () => {
    try {
      const res = await fetch(`${backendConfig.datasetServiceUrl}/api/v1/entities`);
      if (res.ok) {
        const data: EntitySummaryResponse[] = await res.json();
        setSavedEntities(data);
      }
    } catch {
      // Background catalog fetch failure is non-blocking
    }
  }, []);

  useEffect(() => {
    fetchCatalog();
  }, [fetchCatalog]);

  const pollJobStatus = useCallback((jobId: string) => {
    if (pollingRef.current) clearInterval(pollingRef.current);

    pollingRef.current = setInterval(async () => {
      try {
        const res = await fetch(`${backendConfig.researchServiceUrl}/api/v1/research/jobs/${jobId}`);
        if (!res.ok) {
          throw new Error(`Job status check failed: HTTP ${res.status}`);
        }
        const job: ResearchJobResponse = await res.json();
        setCurrentJob(job);

        if (job.status === "COMPLETED") {
          if (pollingRef.current) clearInterval(pollingRef.current);
          setIsSubmitting(false);
          if (job.result) {
            setResearchResult(job.result);
          }
          fetchCatalog();
        } else if (job.status === "FAILED") {
          if (pollingRef.current) clearInterval(pollingRef.current);
          setIsSubmitting(false);
          setErrorMessage(job.error || "Research job encountered an execution failure");
        }
      } catch (err: unknown) {
        if (pollingRef.current) clearInterval(pollingRef.current);
        setIsSubmitting(false);
        setErrorMessage(err instanceof Error ? err.message : "Error polling job status");
      }
    }, 1000);
  }, [fetchCatalog]);

  const handleStartResearch = async (asyncMode: boolean) => {
    setErrorMessage(null);
    setResearchResult(null);
    setCurrentJob(null);
    setIsSubmitting(true);

    const payload: ResearchRequest = {
      url: url.trim(),
      name: name.trim() || undefined,
      entityType: entityType,
    };

    if (asyncMode) {
      try {
        const res = await fetch(`${backendConfig.researchServiceUrl}/api/v1/research/jobs`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });

        if (!res.ok) {
          const errData = await res.json().catch(() => null);
          throw new Error(errData?.detail || `Submission failed with HTTP ${res.status}`);
        }

        const job: ResearchJobResponse = await res.json();
        setCurrentJob(job);
        pollJobStatus(job.jobId);
      } catch (err: unknown) {
        setIsSubmitting(false);
        setErrorMessage(err instanceof Error ? err.message : "Failed to submit research job");
      }
    } else {
      // Synchronous quick execution
      try {
        const res = await fetch(`${backendConfig.researchServiceUrl}/api/v1/research`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        });

        if (!res.ok) {
          const errData = await res.json().catch(() => null);
          throw new Error(errData?.detail || `Research failed with HTTP ${res.status}`);
        }

        const data: ResearchResponse = await res.json();
        setResearchResult(data);
        setIsSubmitting(false);
        fetchCatalog();
      } catch (err: unknown) {
        setIsSubmitting(false);
        setErrorMessage(err instanceof Error ? err.message : "Synchronous research request failed");
      }
    }
  };

  const loadEntityDetail = async (entityId: string) => {
    setLoadingDetail(true);
    try {
      const res = await fetch(`${backendConfig.datasetServiceUrl}/api/v1/entities/${entityId}`);
      if (res.ok) {
        const data: EntityDetailResponse = await res.json();
        setSelectedEntity(data);
      }
    } catch {
      // Ignore
    } finally {
      setLoadingDetail(false);
    }
  };

  const renderConfidenceBadge = (confidence?: ConfidenceTier | string) => {
    const tier = (confidence || "LOW").toUpperCase();
    if (tier === "HIGH") {
      return (
        <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300">
          HIGH
        </span>
      );
    }
    if (tier === "MEDIUM") {
      return (
        <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">
          MEDIUM
        </span>
      );
    }
    return (
      <span className="px-2 py-0.5 text-xs font-semibold rounded-full bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-400">
        LOW
      </span>
    );
  };

  return (
    <div className="min-h-screen bg-zinc-50 dark:bg-zinc-950 text-zinc-900 dark:text-zinc-100 p-4 md:p-8">
      <div className="max-w-6xl mx-auto space-y-6">
        
        {/* Platform Header */}
        <header className="flex flex-col md:flex-row md:items-center md:justify-between pb-4 border-b border-zinc-200 dark:border-zinc-800 gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Data Enrichment AI Intelligence Platform</h1>
            <p className="text-sm text-zinc-500 dark:text-zinc-400 mt-0.5">
              Production-Oriented Web Discovery, Grounded AI Fact Extraction &amp; Relational Persistence
            </p>
          </div>
          <div className="flex items-center gap-2 text-xs font-mono">
            <span className="px-2.5 py-1 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
              Research :9741
            </span>
            <span className="px-2.5 py-1 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
              AI :9742
            </span>
            <span className="px-2.5 py-1 rounded bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
              Dataset :9743
            </span>
          </div>
        </header>

        {/* Tab Navigation */}
        <nav className="flex space-x-2 border-b border-zinc-200 dark:border-zinc-800 pb-2">
          <button
            onClick={() => setActiveTab("research")}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors ${
              activeTab === "research"
                ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                : "text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-900"
            }`}
          >
            Research Engine
          </button>
          <button
            onClick={() => {
              setActiveTab("catalog");
              fetchCatalog();
            }}
            className={`px-4 py-2 text-sm font-medium rounded-lg transition-colors flex items-center gap-2 ${
              activeTab === "catalog"
                ? "bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
                : "text-zinc-600 dark:text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-900"
            }`}
          >
            <span>Persisted Entity Catalog</span>
            {savedEntities.length > 0 && (
              <span className="px-1.5 py-0.2 text-xs rounded-full bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                {savedEntities.length}
              </span>
            )}
          </button>
        </nav>

        {/* Error Alert */}
        {errorMessage && (
          <div className="p-4 rounded-lg bg-rose-50 dark:bg-rose-950/50 border border-rose-200 dark:border-rose-900 text-rose-800 dark:text-rose-200 text-sm flex justify-between items-center">
            <span>{errorMessage}</span>
            <button onClick={() => setErrorMessage(null)} className="font-bold ml-4">
              &times;
            </button>
          </div>
        )}

        {/* TAB 1: RESEARCH ENGINE */}
        {activeTab === "research" && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Left: Input Submission Form */}
            <div className="lg:col-span-1 space-y-4 bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm h-fit">
              <h2 className="text-base font-semibold">Entity Research Seed</h2>
              
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-medium text-zinc-500 dark:text-zinc-400 mb-1">
                    Canonical / Seed URL *
                  </label>
                  <input
                    type="url"
                    value={url}
                    onChange={(e) => setUrl(e.target.value)}
                    placeholder="https://github.com/spring-projects/spring-boot"
                    required
                    className="w-full px-3 py-2 text-sm rounded-lg border border-zinc-300 dark:border-zinc-700 bg-transparent focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:focus:ring-zinc-100"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-zinc-500 dark:text-zinc-400 mb-1">
                    Display Name (Optional Hint)
                  </label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Spring Boot"
                    className="w-full px-3 py-2 text-sm rounded-lg border border-zinc-300 dark:border-zinc-700 bg-transparent focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:focus:ring-zinc-100"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium text-zinc-500 dark:text-zinc-400 mb-1">
                    Entity Type
                  </label>
                  <select
                    value={entityType}
                    onChange={(e) => setEntityType(e.target.value as EntityType)}
                    className="w-full px-3 py-2 text-sm rounded-lg border border-zinc-300 dark:border-zinc-700 bg-transparent focus:outline-none focus:ring-2 focus:ring-zinc-900 dark:focus:ring-zinc-100"
                  >
                    <option value="REPOSITORY">REPOSITORY</option>
                    <option value="ORGANIZATION">ORGANIZATION</option>
                    <option value="PERSON">PERSON</option>
                    <option value="PRODUCT">PRODUCT</option>
                    <option value="WEBSITE">WEBSITE</option>
                    <option value="OTHER">OTHER</option>
                  </select>
                </div>
              </div>

              <div className="pt-3 space-y-2">
                <button
                  type="button"
                  disabled={isSubmitting || !url}
                  onClick={() => handleStartResearch(true)}
                  className="w-full py-2.5 px-4 rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 font-medium text-sm hover:opacity-90 disabled:opacity-50 transition-all flex justify-center items-center gap-2"
                >
                  {isSubmitting ? (
                    <>
                      <span className="animate-spin inline-block w-4 h-4 border-2 border-current border-t-transparent rounded-full" />
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
                  Synchronous Direct Run
                </button>
              </div>

              {/* Quick test seeds */}
              <div className="pt-4 border-t border-zinc-100 dark:border-zinc-800 text-xs text-zinc-500 space-y-1">
                <div className="font-semibold uppercase text-[10px] tracking-wider text-zinc-400">Quick Seeds:</div>
                <button
                  type="button"
                  onClick={() => {
                    setUrl("https://github.com/spring-projects/spring-boot");
                    setName("Spring Boot");
                    setEntityType("REPOSITORY");
                  }}
                  className="block text-left text-zinc-600 dark:text-zinc-400 hover:underline"
                >
                  &bull; Spring Boot (GitHub Repository)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setUrl("https://spring.io");
                    setName("Spring");
                    setEntityType("ORGANIZATION");
                  }}
                  className="block text-left text-zinc-600 dark:text-zinc-400 hover:underline"
                >
                  &bull; Spring.io (Organization)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setUrl("https://example.com/profiles/jane-doe");
                    setName("Jane Doe");
                    setEntityType("PERSON");
                  }}
                  className="block text-left text-zinc-600 dark:text-zinc-400 hover:underline"
                >
                  &bull; Jane Doe (Person Profile)
                </button>
              </div>
            </div>

            {/* Right: Job Polling & Results */}
            <div className="lg:col-span-2 space-y-6">
              
              {/* Asynchronous Job Tracker */}
              {currentJob && (
                <div className="bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm space-y-3">
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Job Tracker</span>
                      <div className="font-mono text-xs text-zinc-600 dark:text-zinc-300">{currentJob.jobId}</div>
                    </div>
                    <div>
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
                  </div>

                  {/* Progress Bar */}
                  <div className="w-full bg-zinc-100 dark:bg-zinc-800 rounded-full h-2 overflow-hidden">
                    <div
                      className="bg-zinc-900 dark:bg-zinc-100 h-2 transition-all duration-300"
                      style={{ width: `${currentJob.progress}%` }}
                    />
                  </div>

                  <div className="flex justify-between text-xs text-zinc-500">
                    <span>Progress: {currentJob.progress}%</span>
                    <span>
                      {currentJob.durationMs != null
                        ? `Duration: ${currentJob.durationMs}ms`
                        : "Executing background research pipeline..."}
                    </span>
                  </div>
                </div>
              )}

              {/* Research Result Presentation */}
              {researchResult && (
                <div className="space-y-6">
                  
                  {/* Entity Summary Card */}
                  <div className="bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm space-y-3">
                    <div className="flex flex-wrap justify-between items-start gap-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="text-xl font-bold">{researchResult.result.displayName}</h3>
                          <span className="px-2 py-0.5 text-xs font-semibold rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                            {researchResult.result.entityType}
                          </span>
                        </div>
                        <a
                          href={researchResult.result.canonicalUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="text-xs text-blue-600 dark:text-blue-400 hover:underline break-all"
                        >
                          {researchResult.result.canonicalUrl}
                        </a>
                      </div>
                      <div className="text-right text-xs text-zinc-500">
                        <span className="font-mono block">Latency: {researchResult.executionTimeMs}ms</span>
                        <span className="font-mono text-[10px] text-zinc-400 truncate max-w-[200px] block">
                          ID: {researchResult.entityId}
                        </span>
                      </div>
                    </div>

                    {/* Warnings Banner if any */}
                    {researchResult.warnings && researchResult.warnings.length > 0 && (
                      <div className="p-3 bg-amber-50 dark:bg-amber-950/40 border border-amber-200 dark:border-amber-900 rounded-lg text-xs text-amber-800 dark:text-amber-200 space-y-1">
                        <div className="font-semibold">Pipeline Diagnostics:</div>
                        <ul className="list-disc list-inside space-y-0.5">
                          {researchResult.warnings.map((w, idx) => (
                            <li key={idx}>{w}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>

                  {/* Extracted Attributes & Evidence Tuples */}
                  <div className="bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm space-y-4">
                    <div className="flex items-center justify-between">
                      <h4 className="text-base font-semibold">Verified Attributes &amp; Evidence</h4>
                      <span className="text-xs text-zinc-500">
                        {Object.keys(researchResult.result.attributes).length} extracted
                      </span>
                    </div>

                    {Object.keys(researchResult.result.attributes).length === 0 ? (
                      <div className="text-sm text-zinc-500 py-4 text-center">
                        No factual attributes verified for this entity.
                      </div>
                    ) : (
                      <div className="divide-y divide-zinc-100 dark:divide-zinc-800">
                        {Object.entries(researchResult.result.attributes).map(([attrKey, tuple]) => (
                          <div key={attrKey} className="py-3 space-y-1.5">
                            <div className="flex items-center justify-between">
                              <span className="font-medium text-sm capitalize">{attrKey.replace(/_/g, " ")}</span>
                              <div className="flex items-center gap-2">
                                {tuple.conflictDetected && (
                                  <span className="px-2 py-0.5 text-[10px] font-semibold rounded bg-rose-100 text-rose-800 dark:bg-rose-950 dark:text-rose-300">
                                    Conflict Resolved
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

                            <p className="text-sm text-zinc-800 dark:text-zinc-200 bg-zinc-50 dark:bg-zinc-800/50 p-2.5 rounded-lg">
                              {tuple.value}
                            </p>

                            {tuple.evidenceSnippet && (
                              <div className="text-xs text-zinc-500 dark:text-zinc-400 italic">
                                &ldquo;{tuple.evidenceSnippet}&rdquo;
                              </div>
                            )}

                            {tuple.sourceUrl && (
                              <div className="text-[11px] text-zinc-400">
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

                  {/* Discovered & Ranked Sources */}
                  <div className="bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-sm space-y-4">
                    <div className="flex items-center justify-between">
                      <h4 className="text-base font-semibold">Discovered &amp; Ranked Sources</h4>
                      <span className="text-xs text-zinc-500">{researchResult.sources.length} sources</span>
                    </div>

                    <div className="divide-y divide-zinc-100 dark:divide-zinc-800">
                      {researchResult.sources.map((src, idx) => (
                        <div key={idx} className="py-3 flex flex-col md:flex-row md:items-center justify-between gap-2">
                          <div className="space-y-1 max-w-xl">
                            <div className="flex items-center gap-2">
                              <span className="px-2 py-0.5 text-[10px] font-semibold rounded bg-zinc-100 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300">
                                {src.sourceType}
                              </span>
                              <span className="text-xs font-semibold truncate">
                                {src.title || src.url}
                              </span>
                            </div>
                            <a
                              href={src.url}
                              target="_blank"
                              rel="noreferrer"
                              className="text-xs text-blue-500 hover:underline break-all block"
                            >
                              {src.url}
                            </a>
                            {src.snippet && (
                              <p className="text-xs text-zinc-500 line-clamp-2">{src.snippet}</p>
                            )}
                          </div>

                          <div className="text-right text-xs shrink-0 font-mono">
                            {src.relevance != null && (
                              <div className="text-zinc-600 dark:text-zinc-300">
                                Relevance: {(src.relevance * 100).toFixed(0)}%
                              </div>
                            )}
                            <div className="text-[10px] text-zinc-400">
                              {new Date(src.retrievedAt).toLocaleTimeString()}
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>

                </div>
              )}

              {/* Initial Placeholder */}
              {!currentJob && !researchResult && (
                <div className="bg-white dark:bg-zinc-900 p-12 rounded-xl border border-zinc-200 dark:border-zinc-800 text-center text-zinc-400 space-y-2">
                  <div className="text-3xl">&infin;</div>
                  <div className="text-sm font-medium">Ready to Research</div>
                  <p className="text-xs text-zinc-500 max-w-sm mx-auto">
                    Submit an entity URL on the left to trigger search discovery, polite content fetching, zero-hallucination AI fact extraction, and relational persistence.
                  </p>
                </div>
              )}

            </div>
          </div>
        )}

        {/* TAB 2: PERSISTED ENTITY CATALOG */}
        {activeTab === "catalog" && (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold">Persisted Entities in Dataset Service</h2>
                <p className="text-xs text-zinc-500">Backed by MySQL Database via Spring Data JPA</p>
              </div>
              <button
                onClick={fetchCatalog}
                className="px-3 py-1.5 text-xs font-medium rounded-lg border border-zinc-300 dark:border-zinc-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors"
              >
                Refresh
              </button>
            </div>

            {savedEntities.length === 0 ? (
              <div className="bg-white dark:bg-zinc-900 p-12 rounded-xl border border-zinc-200 dark:border-zinc-800 text-center text-zinc-400">
                No entities saved in the database yet. Run a research query to auto-persist.
              </div>
            ) : (
              <div className="bg-white dark:bg-zinc-900 rounded-xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-sm">
                <table className="w-full text-left text-sm">
                  <thead className="bg-zinc-50 dark:bg-zinc-800/50 border-b border-zinc-200 dark:border-zinc-800 text-xs font-semibold text-zinc-500">
                    <tr>
                      <th className="p-4">Entity</th>
                      <th className="p-4">Type</th>
                      <th className="p-4">Canonical URL</th>
                      <th className="p-4">Sources</th>
                      <th className="p-4">Attributes</th>
                      <th className="p-4">Updated</th>
                      <th className="p-4 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                    {savedEntities.map((ent) => (
                      <tr key={ent.entityId} className="hover:bg-zinc-50/50 dark:hover:bg-zinc-800/30 transition-colors">
                        <td className="p-4 font-medium">{ent.displayName}</td>
                        <td className="p-4">
                          <span className="px-2 py-0.5 text-xs font-medium rounded bg-zinc-100 dark:bg-zinc-800">
                            {ent.entityType}
                          </span>
                        </td>
                        <td className="p-4 text-xs font-mono text-zinc-500 truncate max-w-[200px]">
                          {ent.canonicalUrl}
                        </td>
                        <td className="p-4 text-xs">{ent.sourcesCount}</td>
                        <td className="p-4 text-xs">{ent.attributesCount}</td>
                        <td className="p-4 text-xs text-zinc-400">
                          {new Date(ent.updatedAt).toLocaleDateString()}
                        </td>
                        <td className="p-4 text-right">
                          <button
                            onClick={() => loadEntityDetail(ent.entityId)}
                            className="px-3 py-1 text-xs font-medium rounded bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 hover:opacity-90"
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

            {/* Entity Detail Inspection Modal / Card */}
            {selectedEntity && (
              <div className="bg-white dark:bg-zinc-900 p-6 rounded-xl border border-zinc-200 dark:border-zinc-800 shadow-md space-y-4">
                <div className="flex justify-between items-start">
                  <div>
                    <h3 className="text-xl font-bold">{selectedEntity.displayName}</h3>
                    <div className="text-xs font-mono text-zinc-500">ID: {selectedEntity.entityId}</div>
                  </div>
                  <button
                    onClick={() => setSelectedEntity(null)}
                    className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 font-bold"
                  >
                    Close
                  </button>
                </div>

                <div className="space-y-2">
                  <h4 className="text-xs font-semibold uppercase tracking-wider text-zinc-400">Persisted Attributes:</h4>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {Object.entries(selectedEntity.attributes).map(([key, attr]) => (
                      <div key={key} className="p-3 rounded-lg bg-zinc-50 dark:bg-zinc-800/50 space-y-1">
                        <div className="flex justify-between items-center">
                          <span className="text-xs font-medium capitalize">{key.replace(/_/g, " ")}</span>
                          {renderConfidenceBadge(attr.confidence)}
                        </div>
                        <div className="text-sm font-semibold text-zinc-800 dark:text-zinc-200">{attr.value}</div>
                        {attr.evidenceSnippet && (
                          <div className="text-[11px] text-zinc-500 italic">&ldquo;{attr.evidenceSnippet}&rdquo;</div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

          </div>
        )}

      </div>
    </div>
  );
}
