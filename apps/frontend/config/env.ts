/**
 * Centralized Environment Configuration for Backend Microservices
 */

function cleanUrl(url: string | undefined, defaultUrl: string): string {
  const target = (url && url.trim().length > 0) ? url.trim() : defaultUrl;
  return target.replace(/\/+$/, '');
}

const defaultGatewayUrl = cleanUrl(process.env.NEXT_PUBLIC_GATEWAY_URL, 'http://localhost:9738');

export const ENV = {
  GATEWAY_URL: defaultGatewayUrl,
  GATEWAY_API_KEY: process.env.NEXT_PUBLIC_GATEWAY_API_KEY || '',
  RESEARCH_SERVICE_URL: cleanUrl(
    process.env.NEXT_PUBLIC_RESEARCH_SERVICE_URL,
    defaultGatewayUrl
  ),
  AI_SERVICE_URL: cleanUrl(
    process.env.NEXT_PUBLIC_AI_SERVICE_URL,
    defaultGatewayUrl
  ),
  DATASET_SERVICE_URL: cleanUrl(
    process.env.NEXT_PUBLIC_DATASET_SERVICE_URL,
    defaultGatewayUrl
  ),
  AUTH_SERVICE_URL: cleanUrl(
    process.env.NEXT_PUBLIC_AUTH_SERVICE_URL,
    defaultGatewayUrl
  ),
} as const;
