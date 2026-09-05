# API Design

> Status: Active  
> Version: 1.0  
> Last Updated: 2026-09-05

## Purpose

This document details the HTTP API contracts across all microservices in the Data Enrichment AI Intelligence Platform:
- **Research Service** (`:9741`): Research orchestration, autonomous discovery, evidence compilation, and asynchronous job execution.
- **AI Intelligent Service** (`:9742`): LLM extraction and structured fallback inference.
- **Dataset Service** (`:9743`): Relational persistence, catalog querying, and pagination.

---

## 1. Platform Architecture & Data Flow

```text
HTTP Client / Next.js UI (:3000)
       │
       ├─────────────────────────────┬─────────────────────────────┐
       ▼                             ▼                             ▼
POST /api/v1/research/jobs   GET /api/v1/research/jobs/{id}  GET /api/v1/entities?page=0&size=20
(research-service :9741)     (research-service :9741)        (dataset-service :9743)
       │
       ├─► 1. Normalize Canonical URL (SHA-256 ID)
       ├─► 2. Polite Scrape Seed Page
       ├─► 3. Discover Candidates (Tavily/Mock)
       ├─► 4. Filter, Fetch, and Extract Snippets
       ├─► 5. Request Extraction (ai-intelligent-service :9742)
       ├─► 6. Multi-Source Corroboration Engine
       └─► 7. Persist Snapshot (dataset-service :9743)
```

---

## 2. Research Service (`:9741`)

### 2.1 Asynchronous Research Job (`POST /api/v1/research/jobs`)

Initiates an asynchronous research job managed by a bounded `ThreadPoolExecutor` (core: 4, max: 16, queue: 500). Returns immediately with a job ID for client polling.

* **Method**: `POST`
* **Path**: `/api/v1/research/jobs`
* **Content-Type**: `application/json`

#### Request Payload
```json
{
  "url": "https://github.com/torvalds?utm_source=twitter&ref=share",
  "entityType": "PERSON",
  "name": "Linus Torvalds"
}
```

#### Response (`202 Accepted`)
```json
{
  "jobId": "job-a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "SUBMITTED",
  "seedUrl": "https://github.com/torvalds?utm_source=twitter&ref=share",
  "submittedAt": "2026-09-05T09:00:00Z",
  "completedAt": null,
  "durationMs": null,
  "result": null,
  "warnings": []
}
```

### 2.2 Get Research Job Status (`GET /api/v1/research/jobs/{jobId}`)

Polls the state and results of a submitted job.

* **Method**: `GET`
* **Path**: `/api/v1/research/jobs/{jobId}`

#### Response (`200 OK` - Completed with Corroboration)
```json
{
  "jobId": "job-a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "COMPLETED",
  "seedUrl": "https://github.com/torvalds?utm_source=twitter&ref=share",
  "submittedAt": "2026-09-05T09:00:00Z",
  "completedAt": "2026-09-05T09:00:03Z",
  "durationMs": 3140,
  "result": {
    "status": "COMPLETED",
    "entityId": "a53e839e944746f3a8b417c88a803975d9e5b8d27376c9ad6ff3db6b931d8e03",
    "result": {
      "displayName": "Linus Torvalds",
      "entityType": "PERSON",
      "canonicalUrl": "https://github.com/torvalds",
      "attributes": {
        "currentRole": {
          "value": "Creator of Linux & Git",
          "sourceUrl": "https://github.com/torvalds",
          "evidenceSnippet": "Creator of Linux and Git at Linux Foundation.",
          "confidence": "HIGH",
          "corroboratingSources": [
            "https://en.wikipedia.org/wiki/Linus_Torvalds"
          ],
          "conflictDetected": false
        },
        "organization": {
          "value": "Linux Foundation",
          "sourceUrl": "https://github.com/torvalds",
          "evidenceSnippet": "Fellow at Linux Foundation.",
          "confidence": "HIGH",
          "corroboratingSources": [
            "https://en.wikipedia.org/wiki/Linus_Torvalds"
          ],
          "conflictDetected": false
        }
      }
    },
    "sources": [
      {
        "url": "https://github.com/torvalds",
        "retrievedAt": "2026-09-05T09:00:01Z",
        "sourceType": "DIRECT_PAGE"
      },
      {
        "url": "https://en.wikipedia.org/wiki/Linus_Torvalds",
        "retrievedAt": "2026-09-05T09:00:02Z",
        "sourceType": "DISCOVERED_SEARCH"
      }
    ],
    "executionTimeMs": 3100,
    "warnings": []
  },
  "warnings": []
}
```

### 2.3 Synchronous Research (`POST /api/v1/research`)

Synchronous execution pipeline for direct programmatic runs. Supports both URL-based research and **discovery-first research** (where entity `name` is provided and `url` is omitted).

* **Method**: `POST`
* **Path**: `/api/v1/research`
* **Content-Type**: `application/json`

#### Request Payload (Standard URL-Based)
```json
{
  "url": "https://github.com/spring-projects/spring-boot",
  "entityType": "REPOSITORY",
  "name": "Spring Boot"
}
```

#### Request Payload (Discovery-First: URL Omitted)
```json
{
  "entityType": "ORGANIZATION",
  "name": "Acme Corporation"
}
```
*When `url` is omitted, the normalizer generates a deterministic canonical URN (`urn:entity:organization:acme-corporation`) and computes a deterministic SHA-256 `entityId`.*

* **Validation Rules**: At least one of `url` or `name` must be provided. If both are omitted or blank, returns `400 Bad Request` with RFC 7807 ProblemDetail.
* **Response**: Returns `ResearchResponse` directly (`200 OK`).

---

## 3. AI Intelligent Service (`:9742`)

### 3.1 Extract Structured Evidence (`POST /api/v1/ai/extract`)

Extracts normalized facts grounded in evidence snippets. Connects to Google Gemini or gracefully falls back to deterministic heuristic extraction if unconfigured.

* **Method**: `POST`
* **Path**: `/api/v1/ai/extract`
* **Request Payload**:
```json
{
  "entityName": "Linus Torvalds",
  "entityType": "PERSON",
  "canonicalUrl": "https://github.com/torvalds",
  "rawTextSnippets": [
    "Linus Torvalds is the principal creator of Linux operating system kernel."
  ]
}
```

---

## 4. Dataset Service (`:9743`)

### 4.1 Persist Enriched Entity (`POST /api/v1/entities`)

Stores or updates the canonical entity, source URLs, and extracted attribute evidence graph in MySQL.

* **Method**: `POST`
* **Path**: `/api/v1/entities`

### 4.2 Get Entity by ID (`GET /api/v1/entities/{entityId}`)

Retrieves the enriched entity record and full evidence provenance.

* **Method**: `GET`
* **Path**: `/api/v1/entities/{entityId}`

### 4.3 List Entities (Paginated) (`GET /api/v1/entities?page=0&size=20`)

Returns paginated entity summaries sorted by `updatedAt DESC`.

* **Method**: `GET`
* **Path**: `/api/v1/entities?page=0&size=20`
* **Query Parameters**:
  - `page` (optional, default: `0`): Zero-based page index.
  - `size` (optional, default: `20`): Page size (max: `100`).
