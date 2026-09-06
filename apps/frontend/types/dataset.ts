/**
 * Types corresponding to Dataset Service DTOs (:9743)
 */

import { EntityType } from './research';
export { type EntityType };

export interface EntitySourceDto {
  url: string;
  title?: string;
  snippet?: string;
  sourceType: string;
  domain?: string;
  provider?: string;
  relevance?: number;
  retrievedAt?: string;
}

export type ColumnRole =
  | 'NAME'
  | 'URL'
  | 'LINKEDIN_URL'
  | 'COMPANY'
  | 'ROLE'
  | 'EMAIL'
  | 'REPOSITORY_URL'
  | 'LOCATION'
  | 'EDUCATION'
  | 'SKILLS'
  | 'UNKNOWN';

export interface ColumnProfile {
  columnName: string;
  detectedRole: ColumnRole;
  completenessPercentage: number;
  populatedCount: number;
  totalCount: number;
  sampleValues: string[];
}

export interface DatasetProfileReport {
  datasetName: string;
  totalRows: number;
  validRows: number;
  malformedRowCount: number;
  duplicateRowCount: number;
  columns: ColumnProfile[];
  detectedEntityColumns: string[];
  detectedUrlColumns: string[];
  detectedOrgColumns: string[];
  existingEnrichedColumns: string[];
  conflictingFields: string[];
  qualityScore: number;
  qualityExplanation: string;
  recommendedMapping: Record<string, string>;
  previewRows: Record<string, string>[];
}

export interface DatasetProfileRequest {
  datasetName: string;
  rows: Record<string, string>[];
}

export type RawRow = Record<string, string>;

export interface ColumnMapping {
  nameColumn?: string;
  urlColumn?: string;
  organizationColumn?: string;
  roleColumn?: string;
  entityTypeColumn?: string;
  [key: string]: string | undefined;
}

export type RowEnrichmentStatus = 'QUEUED' | 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED' | 'CANCELLED';

export interface EntityAttributeDto {
  value: string;
  sourceUrl?: string;
  evidenceSnippet?: string;
  confidence?: string;
}

export interface ExperienceItem {
  role: string;
  organization: string;
  duration?: string;
  summary: string;
  evidenceQuote?: string;
}

export interface ProjectItem {
  title: string;
  description: string;
  technologies: string[];
  sourceUrl?: string;
}

export interface ActivityItem {
  title: string;
  activityType: 'AUTHORED' | 'LIKED' | 'MENTIONED' | 'RECURRING_THEME' | string;
  summary: string;
  classification:
    | 'HIRING_ANNOUNCEMENT'
    | 'TECHNICAL_DISCUSSION'
    | 'INTERNSHIP_POST'
    | 'MENTORSHIP_GUIDANCE'
    | 'LEADERSHIP_INSIGHT'
    | 'GENERAL_UPDATE'
    | string;
  sourceUrl?: string;
}

export interface ResearchProfile {
  currentRole: string;
  currentOrganization: string;
  location: string;
  professionalSummary: string;
  careerBackground: string;
  technicalExpertise: string[];
  relevantExperience: ExperienceItem[];
  relevantProjects: ProjectItem[];
  publicActivity: ActivityItem[];
}

export type PriorityTier = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';

export interface DimensionalScore {
  score: number;
  rationale: string;
}

export interface ObjectiveAssessment {
  overallScore: number;
  priorityTier: PriorityTier;
  whyRelevant: string;
  dimensions: Record<string, DimensionalScore>;
  keyStrengths: string[];
  limitationsOrGaps: string[];
}

export type ApproachType =
  | 'RECRUITER_OUTREACH'
  | 'TECHNICAL_GUIDANCE'
  | 'REFERRAL_REQUEST'
  | 'MENTORSHIP_REQUEST'
  | 'HIRING_CONVERSATION'
  | 'NETWORKING_CONVERSATION'
  | 'NOT_RECOMMENDED';

export interface RecommendedApproach {
  approachType: ApproachType;
  summary: string;
  rationale: string;
  suggestedTalkingPoints: string[];
}

export type FindingType = 'FACT_SOURCE_DERIVED' | 'INFERRED_ASSESSMENT';

export interface ResearchFinding {
  claim: string;
  findingType: FindingType;
  confidence: string;
  sourceUrl?: string;
  evidenceSnippet: string;
  sourceTitle?: string;
}

export interface RowEnrichmentResult {
  rowId: string;
  rowIndex: number;
  originalData: RawRow;
  status: RowEnrichmentStatus;
  displayName: string;
  canonicalUrl: string;
  entityType: string;
  attributes: Record<string, EntityAttributeDto>;
  unresolvedFields: string[];
  conflicts: string[];
  confidence: number;
  sources: EntitySourceDto[];
  errorMessage?: string;
  stage?: string;
  workerId?: string;
  message?: string;
  startedAtMs?: number;
  completedAtMs?: number;
  profile?: ResearchProfile;
  assessment?: ObjectiveAssessment;
  recommendation?: RecommendedApproach;
  findings?: ResearchFinding[];
}

export interface EnrichmentJobRequest {
  datasetName?: string;
  userRequirement?: string;
  defaultEntityType?: string;
  columnMapping?: Record<string, string>;
  rows: RawRow[];
}

export interface EnrichmentJobResponse {
  jobId: string;
  datasetName: string;
  status: 'QUEUED' | 'SUBMITTED' | 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED' | 'CANCELLED';
  userRequirement?: string;
  totalRows: number;
  completedRows: number;
  failedRows: number;
  progress: number;
  createdAt: string;
  completedAt?: string;
  durationMs?: number;
  rowResults: RowEnrichmentResult[];
  errorMessage?: string;
  concurrency?: number;
}

export interface SingleEnrichmentRequest {
  row: RawRow;
  columnMapping: Record<string, string>;
  entityType?: string;
  userRequirement?: string;
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
  sources: EntitySourceDto[];
  attributes: Record<string, EntityAttributeDto>;
  createdAt: string;
  updatedAt: string;
}

/**
 * Unified record representation for Results Table & Modals
 */
export interface EnrichedRecord {
  id: string;
  rowIndex: number;
  originalData: RawRow;
  status: RowEnrichmentStatus;
  displayName?: string;
  canonicalUrl?: string;
  entityType?: EntityType | string;
  attributes?: Record<string, EntityAttributeDto>;
  unresolvedFields?: string[];
  conflicts?: string[];
  confidence?: number;
  sources?: EntitySourceDto[];
  errorMessage?: string;
  stage?: string;
  workerId?: string;
  message?: string;
  startedAtMs?: number;
  completedAtMs?: number;
  profile?: ResearchProfile;
  assessment?: ObjectiveAssessment;
  recommendation?: RecommendedApproach;
  findings?: ResearchFinding[];
  // Preserved for backward-compatible modal inspector
  response?: {
    entityId?: string;
    executionTimeMs?: number;
    warnings?: string[];
    result?: {
      displayName: string;
      entityType: EntityType;
      canonicalUrl: string;
      attributes: Record<
        string,
        {
          value: string;
          sourceUrl: string | null;
          evidenceSnippet: string | null;
          confidence: any;
          corroboratingSources?: string[];
          conflictDetected?: boolean;
        }
      >;
    };
    sources?: any[];
  };
}

/**
 * Real-time Execution Event Model
 */
export interface ExecutionEvent {
  jobId: string;
  rowId: string;
  rowIndex: number;
  entity: string;
  status: RowEnrichmentStatus;
  stage?: string;
  workerId?: string;
  message?: string;
  timestamp: string;
  metadata?: {
    originalData?: RawRow;
    sourcesCount?: number;
    evidenceCount?: number;
    attributesCount?: number;
    confidence?: number;
    error?: string;
    result?: RowEnrichmentResult;
    [key: string]: any;
  };
}

export interface ActiveWorkerState {
  workerId: string;
  rowId?: string;
  rowIndex?: number;
  entity?: string;
  stage?: string;
  message?: string;
  startedAt?: number;
  sourcesCount?: number;
  attributesCount?: number;
}

export interface ExecutionLogEntry {
  id: string;
  timestamp: string;
  timeFormatted: string;
  text: string;
  entity?: string;
  stage?: string;
  workerId?: string;
  type: 'info' | 'success' | 'warn' | 'error';
}

export interface LiveExecutionState {
  job: {
    id: string;
    status: string;
    total: number;
    completed: number;
    failed: number;
    remaining: number;
    concurrency: number;
    durationMs?: number;
    errorMessage?: string;
  };
  rows: Record<string, {
    rowId: string;
    rowIndex: number;
    entity: string;
    status: RowEnrichmentStatus;
    stage: string;
    workerId?: string;
    message?: string;
    startedAt?: number;
    completedAt?: number;
    sourcesCount?: number;
    attributesCount?: number;
    confidence?: number;
    error?: string;
    result?: EnrichedRecord;
  }>;
  activeWorkers: Record<string, ActiveWorkerState>;
  activityLog: ExecutionLogEntry[];
}
