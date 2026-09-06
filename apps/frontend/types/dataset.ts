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

export type RowEnrichmentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'PARTIAL' | 'FAILED';

export interface EntityAttributeDto {
  value: string;
  sourceUrl?: string;
  evidenceSnippet?: string;
  confidence?: string;
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
  status: 'SUBMITTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
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
  conflicts?: string[];
  confidence?: number;
  sources?: EntitySourceDto[];
  errorMessage?: string;
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
