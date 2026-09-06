/**
 * Research Service Client (:9741)
 * Handles direct research execution, asynchronous research jobs, and web discovery.
 */

import { ENV } from '@/config/env';
import { apiClient } from '@/lib/apiClient';
import {
  ResearchRequest,
  ResearchResponse,
  ResearchJobResponse,
} from '@/types/research';

export const researchService = {
  /**
   * Synchronously executes end-to-end research for an entity.
   */
  async executeResearch(request: ResearchRequest): Promise<ResearchResponse> {
    return apiClient<ResearchResponse>(`${ENV.RESEARCH_SERVICE_URL}/api/v1/research`, {
      method: 'POST',
      body: request,
      timeoutMs: 60000,
    });
  },

  /**
   * Submits an asynchronous background research job.
   */
  async submitJob(request: ResearchRequest): Promise<ResearchJobResponse> {
    return apiClient<ResearchJobResponse>(`${ENV.RESEARCH_SERVICE_URL}/api/v1/research/jobs`, {
      method: 'POST',
      body: request,
    });
  },

  /**
   * Polls the status and result of a background research job.
   */
  async getJob(jobId: string): Promise<ResearchJobResponse> {
    return apiClient<ResearchJobResponse>(
      `${ENV.RESEARCH_SERVICE_URL}/api/v1/research/jobs/${encodeURIComponent(jobId)}`,
      {
        method: 'GET',
      }
    );
  },
};
