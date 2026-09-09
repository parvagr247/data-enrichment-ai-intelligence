# Testing & Quality Assurance

This document details the automated testing suites, health probe verification, Postman/Newman end-to-end integration tests, and regression testing workflows.

---

## 1. Automated Test Suites

The backend microservices utilize JUnit 5, AssertJ, and Mockito. All tests can be executed from Maven CLI or directly in an IDE.

### 1.1 Backend Unit & Integration Tests

```bash
# Test AI Intelligent Service
cd apps/backend/ai-intelligent-service
mvn clean test

# Test Research Service
cd apps/backend/research-service
mvn clean test

# Test Dataset Service (Batch coordinator, SSE streaming, profiling)
cd apps/backend/dataset-service
mvn clean test

# Test Auth Service
cd apps/backend/auth-service
mvn clean test
```

### 1.2 Key Test Focus Areas

#### `dataset-service` Test Suite
* **`JobSubmissionRequestTest`**: Validates request DTO constraints, column mapping validations, and empty row rejection.
* **`SseEmitterServiceTest`**: Validates multi-subscriber registry, event broadcasting, client disconnection handling, and graceful emitter cleanup.
* **`BoundedExecutorServiceTest`**: Validates that concurrent thread pools strictly adhere to `enrichment.concurrency.workers` limits and queue rejection semantics.
* **`DatasetProfilingServiceTest`**: Verifies column role classification heuristics (Name, URL, Org, Role), completeness percentages, and quality scoring.
* **`DelimiterSnifferTest`**: Tests delimiter detection across comma, semicolon, tab, and pipe formats.
* **`MalformedRowIsolatorTest`**: Validates non-blocking isolation of malformed CSV rows.

#### `research-service` Test Suite
* **`ResearchExecutionCoordinatorTest`**: Verifies synchronous pipeline execution, URL validation, and attribute corroboration.
* **`SecurityValidatorTest`**: Verifies private IP, localhost, and RFC 1918 blocking for SSRF protection.
* **`ConfidenceScoringHelperTest`**: Validates multi-source corroboration and conflict penalties.

#### `ai-intelligent-service` Test Suite
* **`FactExtractorTest`**: Verifies Spring AI fact extraction and verbatim quote anchoring.
* **`DeterministicFactExtractorFallbackTest`**: Validates fallback extraction when Spring AI is unavailable.
* **`SeedCleanserTest`**: Tests URL normalization and corporate legal suffix stripping.

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
