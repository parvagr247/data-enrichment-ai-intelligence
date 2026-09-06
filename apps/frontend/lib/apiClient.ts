/**
 * Unified, Typed HTTP Client with RFC 7807 Error Extraction
 */

import { ProblemDetail } from '@/types/common';
import { ENV } from '@/config/env';

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  timeoutMs?: number;
}

export class ApiError extends Error {
  public readonly status: number;
  public readonly problem?: ProblemDetail;

  constructor(message: string, status: number, problem?: ProblemDetail) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }
}

export async function apiClient<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const { body, timeoutMs = 30000, headers: customHeaders, ...restOptions } = options;

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  const headers = new Headers(customHeaders);
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  if (ENV.GATEWAY_API_KEY && !headers.has('X-API-Key')) {
    headers.set('X-API-Key', ENV.GATEWAY_API_KEY);
  }
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('enrichment_auth_token');
    if (token && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }

  let finalBody: BodyInit | null = null;
  if (body instanceof FormData) {
    finalBody = body;
    // Note: Do not set Content-Type for FormData; the browser sets it with boundary
  } else if (body !== undefined && body !== null) {
    if (!headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json');
    }
    finalBody = typeof body === 'string' ? body : JSON.stringify(body);
  }

  try {
    const response = await fetch(url, {
      ...restOptions,
      headers,
      body: finalBody,
      signal: restOptions.signal || controller.signal,
    });

    if (!response.ok) {
      let problem: ProblemDetail | undefined;
      let errorMessage = `API request failed with HTTP ${response.status} (${response.statusText})`;

      try {
        const errJson = await response.json();
        if (errJson && typeof errJson === 'object') {
          problem = errJson as ProblemDetail;
          if (problem.detail) {
            errorMessage = problem.detail;
          } else if (problem.title) {
            errorMessage = problem.title;
          } else if (typeof (errJson as any).message === 'string') {
            errorMessage = (errJson as any).message;
          }
        }
      } catch {
        // Response was not JSON
      }

      if (response.status === 401 && typeof window !== 'undefined') {
        const path = window.location.pathname;
        if (!path.startsWith('/login') && !path.startsWith('/register')) {
          localStorage.removeItem('enrichment_auth_token');
          localStorage.removeItem('enrichment_auth_user');
          window.location.href = '/login';
        }
      }

      throw new ApiError(errorMessage, response.status, problem);
    }

    if (response.status === 204) {
      return null as unknown as T;
    }

    return (await response.json()) as T;
  } catch (err: unknown) {
    if (err instanceof ApiError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      throw new ApiError('Request timed out after ' + timeoutMs + 'ms', 408);
    }
    const message = err instanceof Error ? err.message : 'Unknown network failure';
    throw new ApiError(`Network error: ${message}`, 0);
  } finally {
    clearTimeout(timeoutId);
  }
}
