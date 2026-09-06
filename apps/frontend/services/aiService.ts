/**
 * AI Intelligence Service Client (:9742)
 * Handles requirement planning, schema allocation, and structured fact extraction.
 */

import { ENV } from '@/config/env';
import { apiClient } from '@/lib/apiClient';
import {
  EnrichmentRequirement,
  EnrichmentPlan,
  ExtractionRequest,
  ExtractionResponse,
} from '@/types/ai';

export const aiService = {
  /**
   * Plans enrichment fields and strategies based on natural language requirement.
   */
  async planRequirement(requirement: EnrichmentRequirement): Promise<EnrichmentPlan> {
    return apiClient<EnrichmentPlan>(`${ENV.AI_SERVICE_URL}/api/v2/ai/requirements/plan`, {
      method: 'POST',
      body: requirement,
      timeoutMs: 30000,
    });
  },

  /**
   * Extracts structured grounded facts from source text.
   */
  async extractFacts(request: ExtractionRequest): Promise<ExtractionResponse> {
    return apiClient<ExtractionResponse>(`${ENV.AI_SERVICE_URL}/api/v1/ai/extract`, {
      method: 'POST',
      body: request,
      timeoutMs: 30000,
    });
  },
};
