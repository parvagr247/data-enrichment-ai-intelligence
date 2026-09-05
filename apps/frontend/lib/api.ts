/**
 * Consolidated API Boundary & Types for Data Enrichment Engine
 */

// ==========================================
// TYPES
// ==========================================

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
  attributes: Record<
    string,
    {
      value: string;
      sourceUrl?: string;
      evidenceSnippet?: string;
      confidence?: string;
    }
  >;
  createdAt: string;
  updatedAt: string;
}

// Dataset & Workflow Types
export type RawRow = Record<string, string>;

export interface ColumnMapping {
  nameColumn?: string;
  urlColumn?: string;
  organizationColumn?: string;
  roleColumn?: string;
  entityTypeColumn?: string;
}

export type RowEnrichmentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';

export interface EnrichedRecord {
  id: string;
  rowIndex: number;
  originalData: RawRow;
  status: RowEnrichmentStatus;
  errorMessage?: string;
  response?: ResearchResponse;
}

export interface EnrichmentProgressState {
  total: number;
  completed: number;
  processing: number;
  failed: number;
  remaining: number;
  isFinished: boolean;
}

// ==========================================
// CONFIGURATION
// ==========================================

const RESEARCH_URL = (
  process.env.NEXT_PUBLIC_RESEARCH_SERVICE_URL || 'http://localhost:9741'
).replace(/\/+$/, '');

const DATASET_URL = (
  process.env.NEXT_PUBLIC_DATASET_SERVICE_URL || 'http://localhost:9743'
).replace(/\/+$/, '');

// ==========================================
// API CLIENT METHODS
// ==========================================

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `API request failed with HTTP ${res.status}`;
    try {
      const err = await res.json();
      if (err?.detail) message = err.detail;
      else if (err?.title) message = err.title;
    } catch {
      // Fallback to default message
    }
    throw new Error(message);
  }
  return res.json();
}

/**
 * Synchronously executes research for a single request payload.
 */
export async function executeResearch(request: ResearchRequest): Promise<ResearchResponse> {
  const res = await fetch(`${RESEARCH_URL}/api/v1/research`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });
  return handleResponse<ResearchResponse>(res);
}

/**
 * Submits an asynchronous background research job.
 */
export async function submitResearchJob(request: ResearchRequest): Promise<ResearchJobResponse> {
  const res = await fetch(`${RESEARCH_URL}/api/v1/research/jobs`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
    body: JSON.stringify(request),
  });
  return handleResponse<ResearchJobResponse>(res);
}

/**
 * Polls the current status and result of a background research job.
 */
export async function getResearchJob(jobId: string): Promise<ResearchJobResponse> {
  const res = await fetch(`${RESEARCH_URL}/api/v1/research/jobs/${encodeURIComponent(jobId)}`, {
    headers: { 'Accept': 'application/json' },
  });
  return handleResponse<ResearchJobResponse>(res);
}

/**
 * Enriches a single dataset raw row using confirmed column mappings.
 */
export async function enrichSingleRecord(
  row: RawRow,
  mapping: ColumnMapping,
  defaultEntityType: EntityType = 'PERSON'
): Promise<ResearchResponse> {
  const name = mapping.nameColumn ? row[mapping.nameColumn]?.trim() : undefined;
  const url = mapping.urlColumn ? row[mapping.urlColumn]?.trim() : undefined;
  const org = mapping.organizationColumn ? row[mapping.organizationColumn]?.trim() : undefined;
  const role = mapping.roleColumn ? row[mapping.roleColumn]?.trim() : undefined;

  let entityType: EntityType = defaultEntityType;
  if (mapping.entityTypeColumn && row[mapping.entityTypeColumn]) {
    const rawType = row[mapping.entityTypeColumn].trim().toUpperCase();
    if (['PERSON', 'ORGANIZATION', 'PRODUCT', 'REPOSITORY', 'WEBSITE', 'OTHER'].includes(rawType)) {
      entityType = rawType as EntityType;
    }
  }

  return executeResearch({
    url: url || undefined,
    name: name || undefined,
    organization: org || undefined,
    role: role || undefined,
    entityType,
  });
}

/**
 * Fetches the persisted entity catalog from dataset-service.
 */
export async function fetchSavedEntities(): Promise<EntitySummaryResponse[]> {
  const res = await fetch(`${DATASET_URL}/api/v1/entities`, {
    headers: { 'Accept': 'application/json' },
  });
  return handleResponse<EntitySummaryResponse[]>(res);
}

/**
 * Fetches detail for a single persisted entity from dataset-service.
 */
export async function fetchEntityDetail(entityId: string): Promise<EntityDetailResponse> {
  const res = await fetch(`${DATASET_URL}/api/v1/entities/${encodeURIComponent(entityId)}`, {
    headers: { 'Accept': 'application/json' },
  });
  return handleResponse<EntityDetailResponse>(res);
}
