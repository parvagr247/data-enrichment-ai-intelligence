/**
 * Centralized Environment Configuration for Backend Microservices
 */

function cleanUrl(url: string | undefined, defaultUrl: string): string {
  const target = (url && url.trim().length > 0) ? url.trim() : defaultUrl;
  return target.replace(/\/+$/, '');
}

export const ENV = {
  RESEARCH_SERVICE_URL: cleanUrl(
    process.env.NEXT_PUBLIC_RESEARCH_SERVICE_URL,
    'http://localhost:9741'
  ),
  AI_SERVICE_URL: cleanUrl(
    process.env.NEXT_PUBLIC_AI_SERVICE_URL,
    'http://localhost:9742'
  ),
  DATASET_SERVICE_URL: cleanUrl(
    process.env.NEXT_PUBLIC_DATASET_SERVICE_URL,
    'http://localhost:9743'
  ),
} as const;
