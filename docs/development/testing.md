# Testing & Quality Assurance

This document details the automated testing suites, health probe verification, Postman/Newman end-to-end integration tests, and regression testing workflows.

---

## Table of Contents
- [1. Automated Test Suites](#1-automated-test-suites)
  - [1.1 Backend Unit & Integration Tests](#11-backend-unit--integration-tests)
  - [1.2 Key Test Focus Areas](#12-key-test-focus-areas)
- [2. Frontend Verification & Typechecking](#2-frontend-verification--typechecking)
- [3. Health & Liveness Actuator Probes](#3-health--liveness-actuator-probes)
  - [Quick CLI Health Check Script](#quick-cli-health-check-script)
- [4. Postman & Newman End-to-End API Testing](#4-postman--newman-end-to-end-api-testing)
  - [Running Headless Tests with Newman CLI](#running-headless-tests-with-newman-cli)

---

## 1. Automated Test Suites

The backend microservices utilize JUnit 5, AssertJ, and Mockito. All tests can be executed from Maven CLI or directly in an IDE.

### 1.1 Backend Unit & Integration Tests

```bash
# Test AI Intelligent Service (30 tests)
cd apps/backend/ai-intelligent-service
mvn clean test

# Test Research Service (165 tests)
cd apps/backend/research-service
mvn clean test

# Test Dataset Service (36 tests)
cd apps/backend/dataset-service
mvn clean test

# Test Auth Service
cd apps/backend/auth-service
mvn clean test
```

### 1.2 Key Test Focus Areas

#### `dataset-service` Test Suite
* **`DatasetIngestionControllerTest`**: Validates multipart file uploads (CSV, XLSX), automatic delimiter sniffing, and `/profile` request dispatching.
* **`DatasetIngestionServiceTest`**: Validates tabular parsing, header sanitization, column role heuristics (Name, URL, Org, Role), and completeness metrics.
* **`DatasetEnrichmentServiceTest`**: Validates batch enrichment orchestration, `EnrichmentTaskExecutor` concurrency, asynchronous job execution, SSE event streaming, and job state transitions.
* **`EntityControllerTest`**: Validates persisted entity querying, multi-attribute retrieval, and pagination.
* **`UserIsolationAndOwnershipTest`**: Verifies multi-tenant ownership enforcement via `X-User-Id`, ensuring cross-tenant isolation and 403/404 responses on mismatched job/entity access.

#### `research-service` Test Suite
* **`ResearchControllerTest`**: Validates synchronous research (`POST /api/v1/research`) and asynchronous polling (`POST /api/v1/research/jobs`, `GET /api/v1/research/jobs/{jobId}`).
* **`ResearchOrchestratorTest`**: Validates multi-stage pipeline orchestration: search discovery, web fetching, evidence extraction, corroboration, and confidence scoring.
* **`DefaultWebContentFetcherTest`**: Validates SSRF protection (blocking localhost, private subnets, cloud metadata), timeouts, payload size limits, and HTML parsing.
* **`EntityAccuracyAndSourceQualityTest`**: Validates multi-source corroboration, conflict detection, and confidence tier boosting.
* **`SourceRankerTest` & `SourceDeduplicatorTest`**: Validates domain authority ranking, duplicate URL stripping, and content relevance evaluation.
* **`DefaultSourceEvidenceServiceTest` & `FieldExtractorTest`**: Validates attribute extraction, fuzzy matching, and entity name/organization normalization.

#### `ai-intelligent-service` Test Suite
* **`ExtractionControllerTest`**: Validates fact extraction (`/api/v1/ai/extract`), structured enrichment (`/api/v1/ai/enrich`), and requirement planning (`/api/v2/ai/requirements/plan`).
* **`EnrichmentAiServiceTest`**: Validates Spring AI Gemini model interaction, prompt rendering, verbatim quote anchoring, and transparent deterministic heuristic fallback.
* **`EnrichmentPlanningServiceTest`**: Validates unstructured prompt parsing, field objective formulation, and profile assessment.
* **`PromptTemplateServiceTest`**: Validates StringTemplate prompt rendering with strict JSON schema definitions.
* **`AiResponseValidatorTest` & `AiOutputNormalizerTest`**: Validates LLM response JSON schema compliance, markdown fence stripping, and value sanitization.

---

## 2. Frontend Verification & Typechecking

```bash
cd apps/frontend

# Run Next.js linting
npm run lint

# Compile and type-check with Turbopack production build
npm run build
```

---

## 3. Health & Liveness Actuator Probes

Every Spring Boot microservice exposes standard Spring Boot Actuator health endpoints to monitor subsystem readiness (database connection pools, disk space, Eureka registration):

| Service | Healthcheck Endpoint | Expected Response Status |
| :--- | :--- | :--- |
| **API Gateway** | `http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| **Auth Service** | `http://localhost:9739/actuator/health` | `{"status":"UP"}` |
| **Research Service** | `http://localhost:9741/actuator/health` | `{"status":"UP"}` |
| **AI Intelligent Service** | `http://localhost:9742/actuator/health` | `{"status":"UP"}` |
| **Dataset Service** | `http://localhost:9743/actuator/health` | `{"status":"UP"}` |
| **Config Server** | `http://localhost:8888/actuator/health` | `{"status":"UP"}` |
| **Discovery Server** | `http://localhost:8761/actuator/health` | `{"status":"UP"}` |

### Quick CLI Health Check Script
```bash
# Verify all backend health probes
for port in 8080 9739 9741 9742 9743; do
  echo "Checking port $port..."
  curl -s http://localhost:$port/actuator/health | grep -o '"status":"[^"]*"' || echo "Port $port DOWN"
done
```

---

## 4. Postman & Newman End-to-End API Testing

The repository contains an automated API collection for verifying end-to-end user journeys:

* **Collection File**: `postman/data-enrichment-ai-intelligence.postman_collection.json`
* **Local Environment File**: `postman/data-enrichment-local.postman_environment.json`

### Running Headless Tests with Newman CLI
You can execute automated regression suites headlessly using Newman:

```bash
# Install Newman globally or run via npx
npx newman run postman/data-enrichment-ai-intelligence.postman_collection.json \
  -e postman/data-enrichment-local.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export postman-report.json
```

### Test Coverage in Postman Collection
1. **User Authentication Flow**:
   - Register new user.
   - Login and capture JWT Bearer token into environment variable `{{jwt_token}}`.
   - Access `/api/v1/auth/me` with Bearer token.
2. **Synchronous Research Pipeline**:
   - Direct call to `POST /api/v1/research`.
   - Verify canonical URL resolution and attribute confidence tiers.
3. **AI Grounding & Cleansing**:
   - Cleanse raw noisy identity seeds via `POST /api/v1/ai/clean`.
   - Extract facts backed by verbatim quotes via `POST /api/v1/ai/extract`.
4. **Batch Dataset Lifecycle**:
   - Submit multi-row job via `POST /api/v1/enrichment/jobs`.
   - Poll job status via `GET /api/v1/enrichment/jobs/{jobId}` until completed.
   - Query persisted entity catalog via `GET /api/v1/entities`.

---

## 5. End-to-End Regression Verification Workflow

To manually verify the full end-to-end dataset enrichment flow:

1. **Ingest Test Dataset**:
   - Open frontend at [http://localhost:3000](http://localhost:3000).
   - Click **"Load Sample Dataset"** or upload a test CSV.
2. **Verify Profiling & Column Role Detection**:
   - Verify that Name, URL, Organization, and Role are correctly identified.
   - Check that field completeness percentages are displayed.
3. **Submit Batch Enrichment**:
   - Provide user prompt: *"Extract current role, open-source projects, and technical skills"*.
   - Click **"Start Enrichment"**.
4. **Observe Real-Time Concurrent Execution**:
   - Verify SSE connection indicator shows **Connected**.
   - Observe worker cards (`worker-1`, `worker-2`, `worker-3`) transitioning rows through `RESEARCH` &rarr; `AI_EXTRACTION` &rarr; `PERSISTENCE`.
   - Click a completed row to open `EvidenceDetailModal`. Confirm that verbatim snippets and source URLs are rendered.
5. **Verify Non-Destructive Export**:
   - Click **"Export CSV"**.
   - Open the downloaded CSV in Excel or a text editor.
   - Confirm original columns are completely preserved and prefixed `Enriched_*` columns contain verified results.
