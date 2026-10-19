# API Specifications & Contracts

This document details the ingress architecture, authentication, anti-spoofing security protections, distributed tracing, error contract standards (RFC 7807), DTO segregation rules, and the complete catalog of REST and Server-Sent Events (SSE) interfaces across the platform.

---

## Table of Contents
- [1. Gateway Ingress Layer & Ingress Routing](#1-gateway-ingress-layer--ingress-routing)
- [2. Authentication & Ingress Security](#2-authentication--ingress-security)
  - [2.1 JWT Bearer Token Authentication](#21-jwt-bearer-token-authentication)
  - [2.2 Anti-Spoofing Architecture](#22-anti-spoofing-architecture)
  - [2.3 Whitelisted Endpoints](#23-whitelisted-endpoints)
  - [2.4 Injected Security Headers](#24-injected-security-headers)
- [3. Distributed Tracing (X-Correlation-ID)](#3-distributed-tracing-x-correlation-id)
- [4. Standard Error Contract (RFC 7807)](#4-standard-error-contract-rfc-7807)
- [5. Public API DTOs vs. Internal Inter-Service DTOs](#5-public-api-dtos-vs-internal-inter-service-dtos)
- [6. Real-Time Streaming Protocol (SSE)](#6-real-time-streaming-protocol-sse)
- [7. Complete Endpoints Catalog](#7-complete-endpoints-catalog)
  - [7.1 Auth Service (:9739)](#71-auth-service-9739)
  - [7.2 Research Service (:9741)](#72-research-service-9741)
  - [7.3 Dataset Service (:9743)](#73-dataset-service-9743)
  - [7.4 AI Intelligent Service (:9742)](#74-ai-intelligent-service-9742)

---

## 1. Gateway Ingress Layer & Ingress Routing

All external client interactions—including the Next.js Web Frontend, developer SDKs, and third-party integrations—ingress via the **API Gateway** (`:9738` in production, `:8080` in local dev). The Gateway enforces reverse-proxy routing, JWT Bearer token authentication, anti-spoofing header normalization, strict security headers, and centralized Cross-Origin Resource Sharing (CORS).

```
                      Client (Browser / API Consumer)
                                     │
                 Authorization: Bearer <JWT>  (or X-API-Key)
                                     ▼
                ┌─────────────────────────────────────────┐
                │           API Gateway (:8080)           │
                │  - JWT Signature & Expiry Verification  │
                │  - Strip spoofed X-User-* headers       │
                │  - Inject verified X-User-Id / Email    │
                │  - Inject / Propagate X-Correlation-ID  │
                └────────────────────┬────────────────────┘
                                     │
           ┌─────────────────────────┼─────────────────────────┬─────────────────────────┐
           ▼                         ▼                         ▼                         ▼
   auth-service:9739        dataset-service:9743      research-service:9741    ai-intelligent-service:9742
```

### Ingress Routing Table

| Ingress Path Pattern | Target Microservice | Eureka Service ID | Purpose |
| :--- | :--- | :--- | :--- |
| `/api/v1/auth/**` | `auth-service:9739` | `AUTH-SERVICE` | User registration, authentication, token issuance, profile |
| `/api/v1/research/**` | `research-service:9741` | `RESEARCH-SERVICE` | Synchronous & async entity research pipelines |
| `/api/v1/ai/**` | `ai-intelligent-service:9742` | `AI-INTELLIGENT-SERVICE` | Fact extraction, input cleansing, LLM grounding |
| `/api/v2/ai/**` | `ai-intelligent-service:9742` | `AI-INTELLIGENT-SERVICE` | AI requirement planning, objective parsing, profile assessment |
| `/api/v1/enrichment/**` | `dataset-service:9743` | `DATASET-SERVICE` | Batch dataset enrichment jobs & SSE real-time events |
| `/api/v1/entities/**` | `dataset-service:9743` | `DATASET-SERVICE` | Persisted entity records, sources, and attributes |
| `/api/v2/datasets/**` | `dataset-service:9743` | `DATASET-SERVICE` | Multipart dataset upload, delimiter sniffing, schema profiling |
| `/actuator/health` | Local Gateway | N/A | Gateway liveness and readiness health probes |
| `/actuator/info` | Local Gateway | N/A | Gateway build and runtime metadata |

---

## 2. Authentication & Ingress Security

### 2.1 JWT Bearer Token Authentication
* **Header**: `Authorization: Bearer <token>`
* Validated signed HMAC-SHA256 tokens (`jwt.secret`). The token payload contains subject (`userId`), `email`, and timestamps.

### 2.2 Anti-Spoofing Architecture
In a microservices mesh, downstream services rely on HTTP headers (e.g., `X-User-Id`) to enforce multi-tenancy.
1. The Gateway uses a custom `HeaderMapRequestWrapper` on every incoming request.
2. It unconditionally **strips any client-supplied** `X-User-Id`, `X-User-Email`, or `X-User-Roles` headers before inspecting credentials.
3. Upon successful JWT verification, the Gateway decodes claims and **injects verified downstream headers**:
   * `X-User-Id`: Extracted securely from the verified JWT `sub` / `userId` claim.
   * `X-User-Email`: Extracted securely from the verified JWT `email` claim.

### 2.3 Whitelisted Endpoints
The following endpoints bypass JWT authentication:
* `POST /api/v1/auth/register` (Account creation)
* `POST /api/v1/auth/login` (Token issuance)
* `/actuator/health` and `/actuator/info` (Monitoring)
* HTTP `OPTIONS` requests (CORS preflights)

### 2.4 Injected Security Headers
Every HTTP response includes:
* `X-Content-Type-Options: nosniff`
* `X-Frame-Options: DENY`
* `Referrer-Policy: strict-origin-when-cross-origin`
* `Permissions-Policy: geolocation=(), microphone=(), camera=()`

---

## 3. Distributed Tracing (`X-Correlation-ID`)

Every request traversing the platform is tracked via a correlation identifier:
* If supplied by the client, the Gateway preserves and propagates it.
* If omitted, the Gateway generates a UUID v4 and injects it into both downstream headers and client response headers.
* Downstream services populate MDC `correlationId` into all SLF4J log patterns (`[corrId=<uuid>]`).

---

## 4. Standard Error Contract (RFC 7807)

All microservices adhere to RFC 7807 **Problem Details for HTTP APIs** for client errors (4xx) and server errors (5xx):

```json
{
  "type": "https://api.enrichment.platform/errors/entity-not-found",
  "title": "Entity Not Found",
  "status": 404,
  "detail": "No entity found with ID 'e8a1d7c4-0000-4000-a000-000000000000'",
  "instance": "/api/v1/entities/e8a1d7c4-0000-4000-a000-000000000000",
  "code": "ENTITY_NOT_FOUND",
  "requestId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "timestamp": "2026-09-10T02:00:00.123Z"
}
```

---

## 5. Public API DTOs vs. Internal Inter-Service DTOs

* **Public API Contracts**: Ingress payload accepted by `@RestController` controllers (enforces JSR-380 validation `@NotBlank`, `@Size`) and response models presented to users.
* **Internal Inter-Service Contracts**: Downstream REST payloads exchanged between peer microservices via `RestClient` (e.g. `ResearchResultPayload`, `FactExtractionPayload`), completely decoupled from external presentation schemas.

---

## 6. Real-Time Streaming Protocol (SSE)

For long-running batch enrichment, the platform provides real-time Server-Sent Events (SSE):
* **Endpoint**: `GET /api/v1/enrichment/jobs/{jobId}/events`
* **Media Type**: `text/event-stream`
* **Lifecycle**:
  - `init`: Transmits `jobId`, `totalRows`, and concurrency (`3`).
  - `execution-event`: Granular state transitions (`RESEARCH`, `AI_EXTRACTION`, `PERSISTENCE`) and row completions.
  - `job-completed`: Terminal event indicating batch completion.
  - Automatically replays the last 500 buffered events upon client reconnection.

---

## 7. Complete Endpoints Catalog

### 7.1 Auth Service (`:9739`)

#### Register Account
* **Endpoint**: `POST /api/v1/auth/register`
* **Access**: Public
* **Request**:
  ```json
  { "name": "Alex Mercer", "email": "alex@example.com", "password": "strongPassword123" }
  ```
* **Response `201 Created`**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": { "id": "c1f72a44-8d4e-4f3b-8219-c6e8bb2e97aa", "name": "Alex Mercer", "email": "alex@example.com" }
  }
  ```

#### Login
* **Endpoint**: `POST /api/v1/auth/login`
* **Access**: Public
* **Request**:
  ```json
  { "email": "alex@example.com", "password": "strongPassword123" }
  ```
* **Response `200 OK`**: Returns JWT token and user profile.

---

### 7.2 Research Service (`:9741`)

#### Synchronous Research
* **Endpoint**: `POST /api/v1/research`
* **Access**: Auth (JWT) / Internal
* **Request**:
  ```json
  {
    "name": "Satya Nadella",
    "organization": "Microsoft",
    "requirements": "Extract current role, career milestones, and university degrees."
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "status": "COMPLETED",
    "result": {
      "entityName": "Satya Nadella",
      "canonicalUrl": "https://news.microsoft.com/exec/satya-nadella/",
      "attributes": {
        "role": { "value": "Chairman and CEO", "confidenceTier": "HIGH", "exactQuote": "Satya Nadella is Chairman and CEO of Microsoft." },
        "organization": { "value": "Microsoft", "confidenceTier": "HIGH", "exactQuote": "joined Microsoft in 1992" }
      },
      "sources": [
        { "url": "https://news.microsoft.com/exec/satya-nadella/", "domain": "microsoft.com", "primarySource": true }
      ]
    }
  }
  ```

#### Asynchronous Research Job Submission & Polling
* `POST /api/v1/research/jobs`: Submits background research job, returns `202 Accepted` with `researchJobId`.
* `GET /api/v1/research/jobs/{jobId}`: Polls research job status (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`).

---

### 7.3 Dataset Service (`:9743`)

#### Multipart Dataset Ingestion
* **Endpoint**: `POST /api/v2/datasets/upload`
* **Access**: Auth (JWT)
* **Form Parameters**: `file` (Multipart CSV/XLSX)
* **Response `200 OK`**: Returns parsed headers, row count, detected delimiter, and malformed rows.

#### Dataset Schema Profiling
* **Endpoint**: `POST /api/v2/datasets/profile`
* **Access**: Auth (JWT)
* **Request**: JSON array of sample rows and headers.
* **Response `200 OK`**: Column role classifications (`NAME`, `URL`, `ORGANIZATION`, `ROLE`), completeness percentages, and identity candidate column recommendations.

#### Submit Batch Enrichment Job
* **Endpoint**: `POST /api/v1/enrichment/jobs`
* **Access**: Auth (JWT)
* **Request**:
  ```json
  {
    "datasetName": "executive_leads.csv",
    "columnMappings": { "Name": "NAME", "Company": "ORGANIZATION" },
    "userRequirements": "Extract current executive role and primary website.",
    "rows": [
      { "rowIndex": 0, "rowData": { "Name": "Linus Torvalds", "Company": "Linux Foundation" } },
      { "rowIndex": 1, "rowData": { "Name": "Satya Nadella", "Company": "Microsoft" } }
    ]
  }
  ```
* **Response `202 Accepted`**:
  ```json
  {
    "jobId": "e9c1d8a4-0000-4000-a000-000000000000",
    "status": "ACCEPTED",
    "totalRows": 2,
    "concurrency": 3,
    "eventsUrl": "/api/v1/enrichment/jobs/e9c1d8a4-0000-4000-a000-000000000000/events"
  }
  ```

#### Real-Time SSE Stream
* **Endpoint**: `GET /api/v1/enrichment/jobs/{jobId}/events`
* **Access**: Auth (JWT)
* **Content-Type**: `text/event-stream`

#### Query Persisted Entities
* `GET /api/v1/entities`: Returns paginated catalog of enriched entities for the user.
* `GET /api/v1/entities/{id}`: Returns complete entity record with all corroborated sources and attributes.

---

### 7.4 AI Intelligent Service (`:9742`)

#### Verbatim Fact Extraction
* **Endpoint**: `POST /api/v1/ai/extract`
* **Request**:
  ```json
  {
    "sourceUrl": "https://linuxfoundation.org/about",
    "textContent": "Linus Torvalds is the creator of the Linux kernel and a Fellow at the Linux Foundation.",
    "requestedFields": ["role", "organization"]
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "extractedFacts": {
      "role": { "value": "Creator and Fellow", "exactQuote": "creator of the Linux kernel and a Fellow", "grounded": true },
      "organization": { "value": "Linux Foundation", "exactQuote": "Linux Foundation", "grounded": true }
    }
  }
  ```

#### Input Seed Cleansing
* **Endpoint**: `POST /api/v1/ai/clean`
* **Description**: Strips corporate legal suffixes (`Inc.`, `LLC`), normalizes URLs, and cleans noisy names.

#### Structured Requirement Planning
* **Endpoint**: `POST /api/v2/ai/requirements/plan`
* **Description**: Formulates a field-by-field search and extraction strategy against uploaded dataset columns.
