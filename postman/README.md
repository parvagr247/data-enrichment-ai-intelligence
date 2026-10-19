# Postman Collections & API Validation Suites

This directory contains the production-grade Postman collections, environments, and canonical sample test datasets for the **Data Enrichment AI Intelligence Platform**.

---

## 1. Overview & Available Collections

| File | Scope | Requests | Focus Areas |
| :--- | :--- | :--- | :--- |
| **`data-enrichment-ai-intelligence.postman_collection.json`** | **Full Platform Suite** | **40 Requests (9 Folders)** | End-to-end platform validation: Auth lifecycle, Gateway routing, Dataset parsing/profiling, Batch async enrichment (SSE), Research Engine, AI fact extraction, User-isolated MySQL entities, and Objective profiles. |
| **`data-enrichment-research-engine.postman_collection.json`** | **Research Service Suite** | **25 Requests (6 Folders)** | Deep isolated testing of `research-service` (:9741): Multi-query builder, web crawling guardrails, target-field extraction, and SSRF prevention. |
| **`data-enrichment-local.postman_environment.json`** | **Local Environment** | **10 Variables** | Central environment defining service base URLs, JWT credentials, and dynamically captured identifiers (`authToken`, `userId`, `jobId`, `entityId`, `researchJobId`). |
| **`samples/v1-regression-dataset.csv`** | **Regression Dataset** | **5 Entities** | Baseline tabular test dataset for evaluating parsing, column detection, web discovery, and ambiguity handling. |

---

## 2. Full Platform Collection Structure (`data-enrichment-ai-intelligence`)

```text
Data Enrichment AI Intelligence Platform (40 Requests)
│
├── 00 Authentication (3 requests)
│   ├── 01 Register User (POST {{gatewayBaseUrl}}/api/v1/auth/register -> captures {{authToken}}, {{userId}})
│   ├── 02 Login User (POST {{gatewayBaseUrl}}/api/v1/auth/login -> captures {{authToken}}, {{userId}})
│   └── 03 Get Current User Profile (GET {{gatewayBaseUrl}}/api/v1/auth/me)
│
├── 01 Health Probes (5 requests)
│   ├── API Gateway Health Check (GET {{gatewayBaseUrl}}/actuator/health)
│   ├── Auth Service Health Check (GET {{authBaseUrl}}/actuator/health)
│   ├── Dataset Service Health Check (GET {{datasetBaseUrl}}/actuator/health)
│   ├── Research Service Health Check (GET {{researchBaseUrl}}/actuator/health)
│   └── AI Intelligent Service Health Check (GET {{aiBaseUrl}}/actuator/health)
│
├── 02 Ingestion & Profiling (V2) (4 requests)
│   ├── Upload Dataset File (V2) (POST {{datasetBaseUrl}}/api/v2/datasets/upload)
│   ├── Profile Dataset Quality & Schemas (V2) (POST {{datasetBaseUrl}}/api/v2/datasets/profile)
│   ├── Generate AI Enrichment Plan (V2) (POST {{aiBaseUrl}}/api/v2/ai/requirements/plan)
│   └── Cleanse Dataset Seed Input (POST {{aiBaseUrl}}/api/v1/ai/clean)
│
├── 03 Batch Enrichment Orchestration (4 requests)
│   ├── Start Batch Enrichment Job (POST {{datasetBaseUrl}}/api/v1/enrichment/jobs -> captures {{jobId}})
│   ├── Get Enrichment Job Status (GET {{datasetBaseUrl}}/api/v1/enrichment/jobs/{{jobId}})
│   ├── Subscribe Real-Time Events (SSE) (GET {{datasetBaseUrl}}/api/v1/enrichment/jobs/{{jobId}}/events)
│   └── Cancel Batch Job (POST {{datasetBaseUrl}}/api/v1/enrichment/jobs/{{jobId}}/cancel)
│
├── 04 Research Engine (3 requests)
│   ├── Execute Direct Research (POST {{researchBaseUrl}}/api/v1/research)
│   ├── Start Async Research Job (POST {{researchBaseUrl}}/api/v1/research/jobs -> captures {{researchJobId}})
│   └── Get Research Job Status (GET {{researchBaseUrl}}/api/v1/research/jobs/{{researchJobId}})
│
├── 05 AI Intelligence (3 requests)
│   ├── AI Requirement Interpretation (POST {{aiBaseUrl}}/api/v1/ai/requirement)
│   ├── AI Input Cleansing (POST {{aiBaseUrl}}/api/v1/ai/clean)
│   └── AI Fact Extraction (POST {{aiBaseUrl}}/api/v1/ai/enrich)
│
├── 06 Persisted Entities Catalog (3 requests)
│   ├── Persist Entity Snapshot (POST {{datasetBaseUrl}}/api/v1/entities -> captures {{entityId}})
│   ├── Get Saved Entities Catalog (GET {{datasetBaseUrl}}/api/v1/entities?page=0&size=20)
│   └── Get Entity Detail By ID (GET {{datasetBaseUrl}}/api/v1/entities/{{entityId}})
│
├── 07 V2 Scenarios (Tasks 51-80) (10 requests)
│   ├── Scenario 01: Requirement-Driven Enrichment
│   ├── Scenario 02: Blank Requirement Fallback
│   ├── Scenario 03: Adaptive Research (Target-Field Driven)
│   ├── Scenario 04: Multi-Source Web Discovery
│   ├── Scenario 05: Source Ranking & Reliability
│   ├── Scenario 06: Evidence-Backed Grounded Extraction
│   ├── Scenario 07: Ambiguous Entity Disambiguation
│   ├── Scenario 08: Conflicting Evidence Corroboration
│   ├── Scenario 09: AI Structured Fact Extraction
│   └── Scenario 10: AI Failure Resiliency & Heuristic Fallback
│
└── 08 Objective-Driven Research Profiles (5 requests)
    ├── 01 Parse Research Objective
    ├── 02 Profile Assessment - Recruiter (High Priority)
    ├── 03 Profile Assessment - Staff Architect (High Priority)
    ├── 04 Profile Assessment - Neutral Blank Objective
    └── 05 End-to-End Single Entity Objective Enrichment
```

---

## 3. Environment Variables Reference

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `gatewayBaseUrl` | `http://localhost:9738` | Unified API Gateway ingress point (or `:8080` in dev). |
| `authBaseUrl` | `http://localhost:9739` | Authentication Service direct endpoint. |
| `datasetBaseUrl` | `http://localhost:9743` | Dataset Service direct endpoint. |
| `researchBaseUrl` | `http://localhost:9741` | Research Service direct endpoint. |
| `aiBaseUrl` | `http://localhost:9742` | AI Intelligent Service direct endpoint. |
| `authToken` | *(dynamically captured)* | Auto-saved JWT Bearer token from `00 Authentication` requests. |
| `userId` | *(dynamically captured)* | Auto-saved User ID for tenant-isolated catalog queries. |
| `jobId` | *(dynamically captured)* | Auto-saved UUID from batch enrichment jobs. |
| `entityId` | *(dynamically captured)* | Auto-saved SHA-256 entity identifier from persisted snapshots. |
| `researchJobId` | *(dynamically captured)* | Auto-saved UUID from background research jobs. |

---

## 4. Execution via Newman CLI

### Run Complete Platform Suite:
```bash
# Execute the full 40-request platform test suite
newman run postman/data-enrichment-ai-intelligence.postman_collection.json \
  -e postman/data-enrichment-local.postman_environment.json \
  --reporters cli
```

### Run Authenticated Flow (Auth -> Persisted Entities):
```bash
newman run postman/data-enrichment-ai-intelligence.postman_collection.json \
  -e postman/data-enrichment-local.postman_environment.json \
  --folder "00 Authentication" \
  --folder "06 Persisted Entities Catalog"
```

### Run Research Engine Regression Suite:
```bash
newman run postman/data-enrichment-research-engine.postman_collection.json \
  -e postman/data-enrichment-local.postman_environment.json \
  --env-var "baseResearchUrl=http://localhost:9741"
```

---

## 5. Sample Dataset (`samples/`)

The [`samples/v1-regression-dataset.csv`](./samples/v1-regression-dataset.csv) dataset provides a 5-record canonical benchmark:
1. **Linus Torvalds**: High-confidence profile anchor match.
2. **Guido van Rossum**: Multi-signal disambiguation without anchor URL.
3. **Satya Nadella**: Employer-contextual disambiguation.
4. **Martin Fowler**: Custom domain personal authority anchor.
5. **John Smith**: Common name ambiguity preservation (`UNKNOWN` attributes).
