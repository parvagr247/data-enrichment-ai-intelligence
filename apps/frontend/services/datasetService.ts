/**
 * Dataset Service Client (:9743)
 * Handles ingestion, profiling, batch enrichment orchestration, and entity persistence.
 */

import { ENV } from '@/config/env';
import { apiClient } from '@/lib/apiClient';
import {
  DatasetProfileReport,
  DatasetProfileRequest,
  EnrichmentJobRequest,
  EnrichmentJobResponse,
  SingleEnrichmentRequest,
  RowEnrichmentResult,
  EntitySummaryResponse,
  EntityDetailResponse,
  ExecutionEvent,
} from '@/types/dataset';

export const datasetService = {
  /**
   * Uploads and profiles a CSV/XLSX file with delimiter sniffing and quality assessment.
   */
  async uploadAndProfile(file: File): Promise<DatasetProfileReport> {
    const formData = new FormData();
    formData.append('file', file, file.name);

    return apiClient<DatasetProfileReport>(`${ENV.DATASET_SERVICE_URL}/api/v2/datasets/upload`, {
      method: 'POST',
      body: formData,
      timeoutMs: 60000,
    });
  },

  /**
   * Profiles raw rows without file upload.
   */
  async profileRows(request: DatasetProfileRequest): Promise<DatasetProfileReport> {
    return apiClient<DatasetProfileReport>(`${ENV.DATASET_SERVICE_URL}/api/v2/datasets/profile`, {
      method: 'POST',
      body: request,
    });
  },

  /**
   * Submits an asynchronous batch enrichment job.
   */
  async submitBatchJob(request: EnrichmentJobRequest): Promise<EnrichmentJobResponse> {
    return apiClient<EnrichmentJobResponse>(`${ENV.DATASET_SERVICE_URL}/api/v1/enrichment/jobs`, {
      method: 'POST',
      body: request,
    });
  },

  /**
   * Polls the live status, progress, and results of a batch enrichment job.
   */
  async getJobStatus(jobId: string): Promise<EnrichmentJobResponse> {
    return apiClient<EnrichmentJobResponse>(
      `${ENV.DATASET_SERVICE_URL}/api/v1/enrichment/jobs/${encodeURIComponent(jobId)}`,
      {
        method: 'GET',
      }
    );
  },

  /**
   * Subscribes to real-time Server-Sent Events for live batch execution observability.
   * Returns an unsubscribe function to close the stream.
   */
  subscribeJobEvents(
    jobId: string,
    callbacks: {
      onInit?: (initData: { jobId: string; datasetName: string; concurrency: number; totalRows: number; status: string }) => void;
      onEvent?: (event: ExecutionEvent) => void;
      onJobCompleted?: (data: { jobId: string; status: string; completedRows: number; failedRows: number; durationMs: number }) => void;
      onError?: (error: any) => void;
    }
  ): () => void {
    const url = `${ENV.DATASET_SERVICE_URL}/api/v1/enrichment/jobs/${encodeURIComponent(jobId)}/events`;
    let eventSource: EventSource | null = null;

    try {
      eventSource = new EventSource(url);

      eventSource.addEventListener('init', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          callbacks.onInit?.(data);
        } catch (err) {
          console.error('Error parsing init event:', err);
        }
      });

      eventSource.addEventListener('execution-event', (e: MessageEvent) => {
        try {
          const event: ExecutionEvent = JSON.parse(e.data);
          callbacks.onEvent?.(event);
        } catch (err) {
          console.error('Error parsing execution event:', err);
        }
      });

      eventSource.addEventListener('job-completed', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data);
          callbacks.onJobCompleted?.(data);
        } catch (err) {
          console.error('Error parsing job-completed event:', err);
        } finally {
          eventSource?.close();
        }
      });

      eventSource.onerror = (err) => {
        callbacks.onError?.(err);
      };
    } catch (err) {
      callbacks.onError?.(err);
    }

    return () => {
      if (eventSource) {
        eventSource.close();
      }
    };
  },

  /**
   * Cancels a running batch enrichment job.
   */
  async cancelJob(jobId: string): Promise<EnrichmentJobResponse> {
    return apiClient<EnrichmentJobResponse>(
      `${ENV.DATASET_SERVICE_URL}/api/v1/enrichment/jobs/${encodeURIComponent(jobId)}/cancel`,
      {
        method: 'POST',
      }
    );
  },

  /**
   * Lists historical batch enrichment jobs.
   */
  async listJobs(): Promise<EnrichmentJobResponse[]> {
    return apiClient<EnrichmentJobResponse[]>(`${ENV.DATASET_SERVICE_URL}/api/v1/enrichment/jobs`, {
      method: 'GET',
    });
  },

  /**
   * Enriches a single row synchronously.
   */
  async enrichSingle(request: SingleEnrichmentRequest): Promise<RowEnrichmentResult> {
    return apiClient<RowEnrichmentResult>(`${ENV.DATASET_SERVICE_URL}/api/v1/enrichment/single`, {
      method: 'POST',
      body: request,
      timeoutMs: 45000,
    });
  },

  /**
   * Fetches persisted entity catalog summaries from MySQL.
   */
  async fetchSavedEntities(): Promise<EntitySummaryResponse[]> {
    return apiClient<EntitySummaryResponse[]>(`${ENV.DATASET_SERVICE_URL}/api/v1/entities`, {
      method: 'GET',
    });
  },

  /**
   * Fetches full attribute & source details for a single persisted entity.
   */
  async fetchEntityDetail(entityId: string): Promise<EntityDetailResponse> {
    return apiClient<EntityDetailResponse>(
      `${ENV.DATASET_SERVICE_URL}/api/v1/entities/${encodeURIComponent(entityId)}`,
      {
        method: 'GET',
      }
    );
  },
};
