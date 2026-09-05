# Postman Collection: Data Enrichment & Research Engine

Production-grade Postman collection and environment for validating, inspecting, and evaluating the **Data Enrichment & Research Engine** (`research-service`).

---

## 1. Overview

The collection is designed to evaluate the core platform capability:
> **"Can sparse entity information be converted into a useful, evidence-backed enriched record?"**

All requests in the collection target real Spring Boot backend endpoints (`/api/v1/research`, `/api/v1/research/jobs`, `/actuator/health`, `/actuator/info`). There are zero fictional endpoints or synthetic mock paths.

### Files
- **Collection**: `postman/data-enrichment-research-engine.postman_collection.json` (and `data-enrichment-ai-intelligence.postman_collection.json`)
- **Environment**: `postman/data-enrichment-local.postman_environment.json`

---

## 2. Collection Structure (25 Requests across 6 Folders)

```
Data Enrichment & Research Engine
│
├── 01 - Health (2 requests)
│   ├── Health Check (GET /actuator/health)
│   └── Service Info (GET /actuator/info)
│
├── 02 - Research - Basic (5 requests)
│   ├── Person (POST /api/v1/research - Linus Torvalds)
│   ├── Organization (POST /api/v1/research - GitHub)
│   ├── Product (POST /api/v1/research - Git SCM)
│   ├── Repository (POST /api/v1/research - torvalds/linux)
│   └── Website (POST /api/v1/research - Example Domain)
│
├── 03 - Research - Input Variations (5 requests)
│   ├── URL Only (POST /api/v1/research - Tim Berners-Lee URL)
│   ├── Name Only (POST /api/v1/research - Guido van Rossum -> URN resolution)
│   ├── Name + Organization (POST /api/v1/research - Satya Nadella + Microsoft)
│   ├── Name + Role (POST /api/v1/research - Demis Hassabis + CEO + DeepMind)
│   └── Sparse Input (POST /api/v1/research - Ada Lovelace name only)
│
├── 04 - Research - Target Fields (3 requests)
│   ├── Person Fields (POST /api/v1/research - Alan Turing with target fields)
│   ├── Organization Fields (POST /api/v1/research - Apache Software Foundation)
│   └── Custom Fields (POST /api/v1/research - Grace Hopper with unverified metric -> UNKNOWN)
│
├── 05 - Validation & Errors (6 requests)
│   ├── Missing Required Input (POST /api/v1/research - 400 Bad Request)
│   ├── Empty Input (POST /api/v1/research - 400 Bad Request)
│   ├── Invalid Entity Type (POST /api/v1/research - 400 Bad Request)
│   ├── Invalid URL (POST /api/v1/research - 400 Bad Request)
│   ├── Blank Name with Missing URL (POST /api/v1/research - 400 Bad Request)
│   └── Nonexistent Research Job (GET /api/v1/research/jobs/{uuid} - 404 Not Found)
│
└── 06 - End-to-End Scenarios (4 requests)
    ├── E2E 01 - Synchronous Research (Captures {{entityId}})
    ├── E2E 02 - Submit Async Research Job (Captures {{researchJobId}})
    ├── E2E 03 - Poll Async Job Status (Queries {{researchJobId}})
    └── E2E 04 - Deep Adaptive Research (Executes depth DEEP)
```

---

## 3. Environment Variables

All requests in the collection use `{{baseResearchUrl}}`.

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `baseResearchUrl` | `http://localhost:8080` | Base URL for the Research Engine. (When running standalone Spring Boot without a gateway, set this to `http://localhost:9741`.) |
| `entityId` | *(dynamically captured)* | Auto-saved SHA-256 entity identifier from research responses. |
| `researchJobId` | *(dynamically captured)* | Auto-saved UUID from background asynchronous research jobs. |

---

## 4. What to Inspect in the Responses

For successful enrichment requests, inspect:
1. **`result.displayName` & `result.canonicalUrl`**: Confirm whether the engine properly resolved the entity's canonical identity (or deterministic `urn:entity:<type>:<slug>` when URL is omitted).
2. **`entityId`**: 64-character deterministic SHA-256 hash.
3. **`result.attributes`**:
   - Evidence-backed attributes include `value`, `sourceUrl`, `evidenceSnippet`, `confidence` (`HIGH`, `MEDIUM`, `LOW`), `corroboratingSources`, and `conflictDetected`.
   - Unverified requested target fields cleanly synthesize `value: "UNKNOWN"` with `confidence: "UNKNOWN"`.
4. **`sources`**: List of ranked discovered web sources with `relevance` scores and provenance metadata.
5. **`metadata`**: Pipeline metrics including `totalSourcesDiscovered`, `totalSourcesRanked`, and `attributesExtracted`.
6. **`warnings`**: Pipeline warnings or diagnostics (e.g. crawler degradation, timeouts).

---

## 5. Automated Tests

The collection includes robust, behavior-driven tests:
- **Status Codes**: Asserts `200 OK`, `202 Accepted`, `400 Bad Request`, and `404 Not Found`.
- **RFC 7807 Compliance**: Asserts Spring Boot `ProblemDetail` structures (`title: "Bad Request"`, `status: 400`, `instance: "/api/v1/research"`, and descriptive `detail`).
- **Schema Contracts**: Verifies data types, non-empty identifiers, valid enum states (`COMPLETED`, `PARTIAL`, `SUBMITTED`, etc.).
- **Dynamic State Passing**: Automatically extracts `entityId` and `researchJobId` to chain requests across `06 - End-to-End Scenarios`.

---

## 6. How to Import and Run

### In Postman GUI:
1. Open Postman.
2. Click **Import** (top-left).
3. Select both:
   - `postman/data-enrichment-research-engine.postman_collection.json`
   - `postman/data-enrichment-local.postman_environment.json`
4. In the environment dropdown (top-right), select **Data Enrichment - Local Environment**.
5. Set `baseResearchUrl` to `http://localhost:9741` (if running research-service directly) or leave `http://localhost:8080` (if running behind reverse proxy).
6. Open the **Collection Runner** to execute individual folders or the entire collection.

### In Newman CLI:
```bash
# Run the entire collection with the local environment
newman run postman/data-enrichment-research-engine.postman_collection.json \
  -e postman/data-enrichment-local.postman_environment.json \
  --env-var "baseResearchUrl=http://localhost:9741"

# Run only the End-to-End Scenarios folder
newman run postman/data-enrichment-research-engine.postman_collection.json \
  -e postman/data-enrichment-local.postman_environment.json \
  --env-var "baseResearchUrl=http://localhost:9741" \
  --folder "06 - End-to-End Scenarios"
```

