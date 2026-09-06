/**
 * Types corresponding to Research Service DTOs (:9741)
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
  conflictDescription?: string;
  sourceType?: string;
  extractionMethod?: string;
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

export interface ResearchRequest {
  url?: string;
  name?: string;
  organization?: string;
  role?: string;
  entityType?: EntityType;
  targetFields?: string[];
  depth?: 'SHALLOW' | 'NORMAL' | 'DEEP';
  userRequirement?: string;
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
  warnings?: string[];
}

export interface ResearchJobResponse {
  jobId: string;
  status: 'SUBMITTED' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  progress: number;
  createdAt: string;
  completedAt?: string | null;
  durationMs?: number | null;
  result?: ResearchResponse | null;
  error?: string | null;
}
