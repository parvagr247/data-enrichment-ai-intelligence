/**
 * Backend Service Configuration
 * 
 * Centralized, typed configuration for backend services.
 * Configured via environment variables with defaults for local development.
 */

export interface BackendServicesConfig {
  researchServiceUrl: string;
  aiIntelligentServiceUrl: string;
  datasetServiceUrl: string;
}

export const backendConfig: BackendServicesConfig = {
  researchServiceUrl:
    process.env.NEXT_PUBLIC_RESEARCH_SERVICE_URL ?? 'http://localhost:9741',
  aiIntelligentServiceUrl:
    process.env.NEXT_PUBLIC_AI_INTELLIGENT_SERVICE_URL ?? 'http://localhost:9742',
  datasetServiceUrl:
    process.env.NEXT_PUBLIC_DATASET_SERVICE_URL ?? 'http://localhost:9743',
};
