/**
 * Types corresponding to AI Intelligence Service DTOs (:9742)
 */

export type FieldStrategy = 'PRIMARY_SOURCE' | 'SECONDARY_DISCOVERY' | 'WEB_SEARCH';

export interface PlannedField {
  fieldKey: string;
  displayName: string;
  description: string;
  strategy: FieldStrategy;
  existingInDataset: boolean;
  priority: number;
}

export interface EnrichmentPlan {
  userGoalSummary: string;
  plannedFields: PlannedField[];
  inferredEntityType: string;
  estimatedSourcesCount: number;
}

export interface EnrichmentRequirement {
  userObjective: string;
  entityType: string;
  existingDatasetColumns?: string[];
  explicitFields?: string[];
}

export interface ExtractedFact {
  value: string;
  exactQuote: string;
  confidenceScore: number;
}

export interface ExtractionRequest {
  entityName: string;
  entityType?: string;
  sourceUrl: string;
  textContent: string;
  targetFields?: string[];
}

export interface ExtractionResponse {
  entityName: string;
  sourceUrl: string;
  facts: Record<string, ExtractedFact>;
  modelUsed: string;
  executionTimeMs: number;
}
