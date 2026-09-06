"use client";

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { EntitySummaryResponse, EntityDetailResponse } from "@/types/dataset";
import { datasetService } from "@/services/datasetService";

export function EntityCatalog() {
  const [entities, setEntities] = useState<EntitySummaryResponse[]>([]);
  const [selectedEntity, setSelectedEntity] = useState<EntityDetailResponse | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchCatalog = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);
      const data = await datasetService.fetchSavedEntities();
      setEntities(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load saved entity catalog");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchCatalog();
  }, [fetchCatalog]);

  const loadEntityDetail = async (entityId: string) => {
    try {
      const data = await datasetService.fetchEntityDetail(entityId);
      setSelectedEntity(data);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load entity detail");
    }
  };

  const filteredEntities = useMemo(() => {
    if (!searchTerm.trim()) return entities;
    const term = searchTerm.toLowerCase();
    return entities.filter(
      (e) =>
        e.displayName.toLowerCase().includes(term) ||
        e.canonicalUrl.toLowerCase().includes(term) ||
        e.entityType.toLowerCase().includes(term)
    );
  }, [entities, searchTerm]);

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-zinc-900 dark:text-zinc-100">
            Persisted Entity Catalog
          </h2>
          <p className="text-xs text-zinc-500">
            Saved entities stored in MySQL by the Dataset Service (:9743).
          </p>
        </div>

        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder="Search catalog..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="text-xs px-3 py-1.5 rounded-lg border border-zinc-300 dark:border-zinc-700 bg-white dark:bg-zinc-900 text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-zinc-900 dark:focus:ring-zinc-100 w-48 sm:w-64"
          />

          <button
            type="button"
            onClick={fetchCatalog}
            disabled={isLoading}
            className="px-3 py-1.5 text-xs font-medium rounded-lg border border-zinc-300 dark:border-zinc-700 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-colors disabled:opacity-50"
          >
            {isLoading ? "Refreshing..." : "Refresh"}
          </button>
        </div>
      </div>

      {error && (
        <div className="p-3 bg-rose-50 dark:bg-rose-950/40 border border-rose-200 dark:border-rose-900 rounded-lg text-xs text-rose-800 dark:text-rose-200">
          {error}
        </div>
      )}

      {filteredEntities.length === 0 ? (
        <div className="bg-white dark:bg-zinc-900 p-12 rounded-xl border border-zinc-200 dark:border-zinc-800 text-center text-zinc-400">
          {entities.length === 0
            ? "No entities saved in the catalog yet. Complete an enrichment run to auto-persist."
            : "No entities matching your search query."}
        </div>
      ) : (
        <div className="bg-white dark:bg-zinc-900 rounded-xl border border-zinc-200 dark:border-zinc-800 overflow-hidden shadow-xs">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-zinc-50 dark:bg-zinc-800/60 border-b border-zinc-200 dark:border-zinc-800 font-semibold text-zinc-500">
                <tr>
                  <th className="p-3">Entity</th>
                  <th className="p-3">Type</th>
                  <th className="p-3">Canonical URL</th>
                  <th className="p-3">Sources</th>
                  <th className="p-3">Attributes</th>
                  <th className="p-3">Updated</th>
                  <th className="p-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-800">
                {filteredEntities.map((ent) => (
                  <tr key={ent.entityId} className="hover:bg-zinc-50/60 dark:hover:bg-zinc-800/40 transition-colors">
                    <td className="p-3 font-semibold text-zinc-900 dark:text-zinc-100">{ent.displayName}</td>
                    <td className="p-3">
                      <span className="px-2 py-0.5 text-[10px] font-semibold rounded bg-zinc-100 dark:bg-zinc-800">
                        {ent.entityType}
                      </span>
                    </td>
                    <td className="p-3 text-zinc-500 font-mono text-[11px] truncate max-w-[200px]">
                      {ent.canonicalUrl}
                    </td>
                    <td className="p-3">{ent.sourcesCount}</td>
                    <td className="p-3">{ent.attributesCount}</td>
                    <td className="p-3 text-zinc-400">
                      {new Date(ent.updatedAt).toLocaleDateString()}
                    </td>
                    <td className="p-3 text-right">
                      <button
                        type="button"
                        onClick={() => loadEntityDetail(ent.entityId)}
                        className="px-2.5 py-1 text-xs font-semibold rounded bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900 hover:opacity-90"
                      >
                        Inspect
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Selected Entity Detail Modal */}
      {selectedEntity && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-950/60 backdrop-blur-xs"
          onClick={() => setSelectedEntity(null)}
        >
          <div
            className="bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 rounded-2xl w-full max-w-2xl max-h-[85vh] flex flex-col shadow-xl overflow-hidden"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="p-5 border-b border-zinc-100 dark:border-zinc-800 flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="text-lg font-bold text-zinc-900 dark:text-zinc-100">
                    {selectedEntity.displayName}
                  </h3>
                  <span className="px-2 py-0.5 text-xs font-semibold rounded bg-zinc-100 dark:bg-zinc-800">
                    {selectedEntity.entityType}
                  </span>
                </div>
                <div className="text-xs font-mono text-zinc-400 mt-0.5">ID: {selectedEntity.entityId}</div>
              </div>
              <button
                type="button"
                onClick={() => setSelectedEntity(null)}
                className="text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-200 text-xl font-bold"
              >
                &times;
              </button>
            </div>

            <div className="p-5 overflow-y-auto space-y-4 text-xs">
              <div>
                <h4 className="font-semibold text-zinc-700 dark:text-zinc-300 uppercase tracking-wider text-[11px] mb-2">
                  Persisted Attributes
                </h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                  {Object.entries(selectedEntity.attributes).map(([k, attr]) => (
                    <div key={k} className="p-3 rounded-lg bg-zinc-50 dark:bg-zinc-800/50 space-y-1">
                      <div className="flex justify-between items-center">
                        <span className="font-semibold text-zinc-700 dark:text-zinc-300 capitalize text-[11px]">
                          {k.replace(/_/g, " ")}
                        </span>
                        {attr.confidence && (
                          <span className="px-1.5 py-0.2 text-[9px] font-semibold rounded bg-zinc-200 dark:bg-zinc-700">
                            {attr.confidence}
                          </span>
                        )}
                      </div>
                      <div className="text-sm font-medium text-zinc-900 dark:text-zinc-100">
                        {attr.value}
                      </div>
                      {attr.evidenceSnippet && (
                        <div className="text-[11px] text-zinc-500 italic">
                          &ldquo;{attr.evidenceSnippet}&rdquo;
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="p-4 border-t border-zinc-100 dark:border-zinc-800 flex justify-end">
              <button
                type="button"
                onClick={() => setSelectedEntity(null)}
                className="px-4 py-2 text-xs font-semibold rounded-lg bg-zinc-900 text-white dark:bg-zinc-100 dark:text-zinc-900"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
