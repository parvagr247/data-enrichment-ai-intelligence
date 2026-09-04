/**
 * Minimal Typed API Contracts for Research & Enrichment
 * Aligned with docs/setup/api-design.md and docs/setup/entity-model.md
 */

export type EntityType =
  | 'PERSON'
  | 'ORGANIZATION'
  | 'PRODUCT'
  | 'REPOSITORY'
  | 'WEBSITE'
  | 'OTHER';

export type ConfidenceTier = 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';

export interface EvidenceTuple {
  value: string;
  sourceUrl: string | null;
  evidenceSnippet: string | null;
  confidence: ConfidenceTier;
}

export interface ResearchRequest {
  url: string;
  entityType?: EntityType;
  name?: string;
}

export interface SourceItem {
  url: string;
  retrievedAt: string;
  sourceType: string;
}

export interface ResearchResult {
  displayName: string;
  entityType: EntityType;
  canonicalUrl: string;
  attributes: Record<string, EvidenceTuple>;
}

export interface ResearchResponse {
  status: 'COMPLETED' | 'FAILED' | 'SUBMITTED' | 'IN_PROGRESS';
  entityId: string;
  result: ResearchResult;
  sources: SourceItem[];
  executionTimeMs: number;
}
