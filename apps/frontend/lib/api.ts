/**
 * Central API Facade re-exporting modular services and types
 */

export * from '@/types/common';
export * from '@/types/research';
export * from '@/types/dataset';
export * from '@/types/ai';
export * from '@/config/env';
export * from '@/lib/apiClient';

import { researchService } from '@/services/researchService';
import { datasetService } from '@/services/datasetService';
import { aiService } from '@/services/aiService';
import {
  ResearchRequest,
  ResearchResponse,
  ResearchJobResponse,
  EntityType,
} from '@/types/research';
import {
  EnrichmentJobRequest,
  EnrichmentJobResponse,
  EntitySummaryResponse,
  EntityDetailResponse,
  ColumnMapping,
  RawRow,
} from '@/types/dataset';

// Export service singletons
export { researchService, datasetService, aiService };

// Backward-compatible delegators
export const executeResearch = (req: ResearchRequest): Promise<ResearchResponse> =>
  researchService.executeResearch(req);

export const submitResearchJob = (req: ResearchRequest): Promise<ResearchJobResponse> =>
  researchService.submitJob(req);

export const getResearchJob = (jobId: string): Promise<ResearchJobResponse> =>
  researchService.getJob(jobId);

export const submitDatasetEnrichmentJob = (
  req: EnrichmentJobRequest
): Promise<EnrichmentJobResponse> => datasetService.submitBatchJob(req);

export const getDatasetEnrichmentJob = (jobId: string): Promise<EnrichmentJobResponse> =>
  datasetService.getJobStatus(jobId);

export const fetchSavedEntities = (): Promise<EntitySummaryResponse[]> =>
  datasetService.fetchSavedEntities();

export const fetchEntityDetail = (id: string): Promise<EntityDetailResponse> =>
  datasetService.fetchEntityDetail(id);

export async function enrichSingleRecord(
  row: RawRow,
  mapping: ColumnMapping,
  defaultEntityType: EntityType = 'PERSON',
  userRequirement?: string
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

  return researchService.executeResearch({
    url: url || undefined,
    name: name || undefined,
    organization: org || undefined,
    role: role || undefined,
    entityType,
    userRequirement: userRequirement?.trim() || undefined,
  });
}
