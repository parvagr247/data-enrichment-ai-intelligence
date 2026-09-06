/**
 * Shared Common API and Application Types
 */

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  timestamp?: string;
  validationErrors?: Record<string, string>;
  [key: string]: unknown;
}

export interface EnrichmentProgressState {
  total: number;
  completed: number;
  processing: number;
  failed: number;
  remaining: number;
  isFinished: boolean;
  statusText?: string;
}

export type ExportFormat = 'csv' | 'xlsx';
