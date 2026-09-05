/**
 * Typed API Contracts for Research & Data Enrichment Platform
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
  corroboratingSources?: string[];
  conflictDetected?: boolean;
}

export interface ResearchRequest {
  url: string;
  entityType?: EntityType;
  name?: string;
}

export interface SourceItem {
  url: string;
  title?: string;
  snippet?: string;
  sourceType: string;
  domain?: string;
  provider?: string;
  relevance?: number;
  retrievedAt: string;
}

export interface ResearchResult {
  displayName: string;
  entityType: EntityType;
  canonicalUrl: string;
  attributes: Record<string, EvidenceTuple>;
}

export interface ResearchResponse {
  status: 'COMPLETED' | 'PARTIAL' | 'FAILED';
  entityId: string;
  result: ResearchResult;
  sources: SourceItem[];
  executionTimeMs: number;
  metadata?: Record<string, unknown>;
  warnings?: string[];
}

export type ResearchJobStatus = 'SUBMITTED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';

export interface ResearchJobResponse {
  jobId: string;
  status: ResearchJobStatus;
  progress: number;
  createdAt: string;
  completedAt?: string | null;
  durationMs?: number | null;
  result?: ResearchResponse | null;
  error?: string | null;
  warnings?: string[];
}

export interface EntitySummaryResponse {
  entityId: string;
  displayName: string;
  entityType: string;
  canonicalUrl: string;
  sourcesCount: number;
  attributesCount: number;
  updatedAt: string;
}

export interface EntityDetailResponse {
  entityId: string;
  displayName: string;
  entityType: string;
  canonicalUrl: string;
  sources: SourceItem[];
  attributes: Record<string, {
    value: string;
    sourceUrl?: string;
    evidenceSnippet?: string;
    confidence?: string;
  }>;
  createdAt: string;
  updatedAt: string;
}
