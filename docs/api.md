# API Reference

This document provides a comprehensive catalog of all REST API endpoints provided by the Spring Boot microservices.

---

## 1. API Gateway & Ingress Layer (`:9738`)

All client interactions (including the Next.js Frontend) route through the **API Gateway** on Port `9738`. The Gateway acts as the single unified entry point, enforcing reverse proxy routing, JWT Bearer authentication, anti-spoofing header normalization, optional API key checks, strict security headers, and centralized CORS.

### Gateway Routing Table

| Ingress Path Pattern | Destination Microservice | Purpose |
| :--- | :--- | :--- |
| `/api/v1/auth/**` | `auth-service:9739` | User registration, authentication, token issuance, profile |
| `/api/v1/research/**` | `research-service:9741` | Synchronous & async entity research pipelines |
| `/api/v1/sources/**` | `research-service:9741` | Discovered source retrieval & verification |
| `/api/v1/ai/**` | `ai-intelligent-service:9742` | Requirement parsing, data cleansing, LLM grounding |
| `/api/v1/enrichment/**` | `dataset-service:9743` | Batch dataset enrichment jobs & SSE events |
| `/api/v1/entities/**` | `dataset-service:9743` | Persisted entity records, sources, and attributes |
| `/actuator/health` | Local Gateway | Gateway liveness and readiness probes |
| `/actuator/info` | Local Gateway | Gateway runtime information |

### Authentication & Ingress Security

1. **JWT Bearer Token Authentication**:
   * Header: `Authorization: Bearer <jwt>`
   * Validates tokens signed with HMAC-SHA256 (`jwt.secret`).
   * **Anti-Spoofing Architecture**: External clients cannot spoof user identity. `HeaderMapRequestWrapper` intercepts all incoming requests, strips any client-supplied `X-User-Id` or `X-User-Email` headers, and injects verified `X-User-Id` and `X-User-Email` headers downstream only after verifying token validity.
2. **Public Whitelisted Endpoints**:
   * `POST /api/v1/auth/register`
   * `POST /api/v1/auth/login`
   * `/actuator/health` and `/actuator/info`
   * HTTP `OPTIONS` requests (CORS preflight)
3. **Optional API Key Check**:
   * Header: `X-API-Key: <key>`
   * Configured via `GATEWAY_API_KEY`. When non-empty, checks that `X-API-Key` matches.

### Gateway Unauthorized Response (`401 Unauthorized`)
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Missing or invalid Bearer token",
  "instance": "/api/v1/enrichment/jobs"
}
```

### Injected Security Headers
Every HTTP response mediated through the gateway includes:
* `X-Content-Type-Options: nosniff`
* `X-Frame-Options: DENY`
* `Referrer-Policy: strict-origin-when-cross-origin`
* `Permissions-Policy: geolocation=(), microphone=(), camera=()`

---

## 2. Services Overview

| Service | Host Port | Internal Port | Primary Role |
| :--- | :--- | :--- | :--- |
| **`api-gateway`** | `9738` | `9738` | Ingress gateway, reverse proxy, JWT auth, anti-spoofing, CORS |
| **`auth-service`** | Internal | `9739` | User accounts, BCrypt passwords, HMAC-SHA256 JWT tokens |
| **`research-service`** | Internal | `9741` | Research execution, web scraping, and evidence collection |
| **`ai-intelligent-service`** | Internal | `9742` | Requirement interpretation, input cleansing, and grounded fact extraction |
| **`dataset-service`** | Internal | `9743` | Batch enrichment jobs, row-level tracking, and MySQL entity persistence |

---

## 3. Auth Service (`:9739`)

### `POST /api/v1/auth/register`
Registers a new user account and returns a signed JWT token.

* **Public**: Yes
* **Headers**: `Content-Type: application/json`
* **Request Body**:
```json
{
  "name": "Alex Mercer",
  "email": "alex@example.com",
  "password": "strongPassword123"
}
```
* **Success Response (`201 Created`)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "c1f72a44-8d4e-4f3b-8219-c6e8bb2e97aa",
    "name": "Alex Mercer",
    "email": "alex@example.com",
    "createdAt": "2026-09-06T20:15:30Z"
  }
}
```
* **Error Responses**:
  * `400 Bad Request`: Validation failure (empty field or invalid email).
  * `409 Conflict`: Email already registered.

### `POST /api/v1/auth/login`
Authenticates user credentials and issues a JWT token.

* **Public**: Yes
* **Headers**: `Content-Type: application/json`
* **Request Body**:
```json
{
  "email": "alex@example.com",
  "password": "strongPassword123"
}
```
* **Success Response (`200 OK`)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "c1f72a44-8d4e-4f3b-8219-c6e8bb2e97aa",
    "name": "Alex Mercer",
    "email": "alex@example.com",
    "createdAt": "2026-09-06T20:15:30Z"
  }
}
```
* **Error Response (`401 Unauthorized`)**:
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid email or password",
  "instance": "/api/v1/auth/login"
}
```

### `GET /api/v1/auth/me`
Retrieves the profile of the currently authenticated user.

* **Public**: No (requires Bearer token or Gateway forwarded `X-User-Id` / `X-User-Email`)
* **Headers**: `Authorization: Bearer <token>`
* **Success Response (`200 OK`)**:
```json
{
  "id": "c1f72a44-8d4e-4f3b-8219-c6e8bb2e97aa",
  "name": "Alex Mercer",
  "email": "alex@example.com",
  "createdAt": "2026-09-06T20:15:30Z"
}
```

---

## 4. Research Service (`:9741`)

### `POST /api/v1/research`
Executes synchronous end-to-end research for an entity.

* **Headers**: `Content-Type: application/json`, `Accept: application/json`
* **Request Body**:
  ```json
  {
    "url": "https://github.com/spring-projects/spring-boot",
    "name": "Spring Boot",
    "organization": "VMware / Broadcom",
    "role": "Framework",
    "entityType": "REPOSITORY",
    "userRequirement": "Extract tech stack, stars, primary language, and license"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "status": "COMPLETED",
    "entityId": "a1b2c3d4...",
    "result": {
      "canonicalUrl": "https://github.com/spring-projects/spring-boot",
      "displayName": "Spring Boot",
      "entityType": "REPOSITORY",
      "attributes": {
        "primary_language": {
          "value": "Java",
          "evidenceSnippet": "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications in Java",
          "confidence": "HIGH",
          "sourceUrl": "https://github.com/spring-projects/spring-boot",
          "corroboratingSources": ["https://github.com/spring-projects/spring-boot"],
          "conflictDetected": false
        }
      }
    },
    "sources": [
      {
        "url": "https://github.com/spring-projects/spring-boot",
        "title": "spring-projects/spring-boot",
        "domain": "github.com",
        "sourceType": "PRIMARY"
      }
    ],
    "executionTimeMs": 1420
  }
  ```

### `POST /api/v1/research/jobs`
Submits an asynchronous background research job.

* **Response `202 Accepted`**:
  ```json
  {
    "jobId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "status": "PENDING",
    "createdAt": "2026-09-06T03:00:00Z"
  }
  ```

### `GET /api/v1/research/jobs/{jobId}`
Polls status and result of a background research job.

* **Response `200 OK`**: Returns job metadata with `status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED"`, and populated `result` when complete.

---

## 5. AI Intelligent Service (`:9742`)

### `POST /api/v1/ai/requirement`
Interprets natural language user requirements into structured search guidance.

* **Request Body**:
  ```json
  {
    "requirement": "Extract tech stack, founders, funding stage, and headquarters",
    "entityType": "ORGANIZATION"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "targetFields": ["tech_stack", "founders", "funding_stage", "headquarters"],
    "focusAreas": ["Technology", "Leadership", "Financials", "Location"],
    "searchKeywords": ["tech stack", "founders", "series funding", "headquarters office"],
    "interpretedSuccessfully": true
  }
  ```

### `POST /api/v1/ai/clean`
Cleans noisy input seeds, normalizes URLs, and strips honorifics or corporate suffixes.

* **Request Body**:
  ```json
  {
    "rawName": "  Dr. Jane Doe, Ph.D. 🚀  ",
    "rawUrl": "https://linkedin.com/in/jane-doe?utm_source=share",
    "rawOrganization": "Acme Corp, Inc.",
    "rawRole": "VP of Engineering & Co-Founder"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "cleanedName": "Jane Doe",
    "cleanedUrl": "https://linkedin.com/in/jane-doe",
    "cleanedOrganization": "Acme Corp",
    "cleanedRole": "VP of Engineering",
    "detectedEntityType": "PERSON",
    "suggestedFields": ["current_role", "organization", "skills", "experience"]
  }
  ```

### `POST /api/v1/ai/enrich`
Extracts structured factual attributes from scraped text strictly backed by verbatim quotes.

* **Request Body**:
  ```json
  {
    "entityName": "Spring Boot",
    "entityType": "REPOSITORY",
    "sourceUrl": "https://github.com/spring-projects/spring-boot",
    "pageContent": "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications. The core language is Java.",
    "targetFields": ["primary_language", "purpose"]
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "attributes": {
      "primary_language": {
        "value": "Java",
        "evidenceSnippet": "The core language is Java.",
        "confidence": "HIGH",
        "sourceUrl": "https://github.com/spring-projects/spring-boot"
      }
    },
    "summary": "Spring Boot is a framework for creating stand-alone Spring applications.",
    "sourceUrl": "https://github.com/spring-projects/spring-boot"
  }
  ```

---

## 6. Dataset Service (`:9743`)

The Dataset Service orchestrates multi-row batch execution and entity persistence. All endpoints accept an optional or injected `X-User-Id` header (automatically populated by the API Gateway after JWT validation) to enforce tenant isolation and IDOR protection.

### Data Ownership & IDOR Protection
* **User Scoping**: When `X-User-Id` is present, batch jobs (`JobState`) and persisted entities are bound to that `userId`.
* **IDOR Prevention**: Calling `GET /api/v1/enrichment/jobs/{jobId}`, `GET .../events`, or `POST .../cancel` on a job owned by another user returns `404 Not Found` (or `403 Forbidden`).
* **Unowned Compatibility**: Requests without `X-User-Id` or legacy records (`user_id IS NULL`) remain accessible to ensure non-breaking backwards compatibility.

### `POST /api/v1/enrichment/jobs`
Submits a batch enrichment job across multiple rows.

* **Request Body**:
  ```json
  {
    "datasetName": "leads-q3.csv",
    "userRequirement": "Find tech stack and company headquarters",
    "defaultEntityType": "ORGANIZATION",
    "columnMapping": {
      "nameColumn": "Company",
      "urlColumn": "Website"
    },
    "rows": [
      { "Company": "Stripe", "Website": "https://stripe.com" },
      { "Company": "Vercel", "Website": "https://vercel.com" }
    ]
  }
  ```
* **Response `202 Accepted`**:
  ```json
  {
    "jobId": "job-d8e27c1a-...",
    "status": "PROCESSING",
    "totalRows": 2,
    "completedRows": 0,
    "failedRows": 0,
    "pendingRows": 2,
    "results": []
  }
  ```

### `GET /api/v1/enrichment/jobs/{jobId}`
Polls status, progress counters, concurrency, and row-level results of a batch enrichment job.

* **Response `200 OK`**: Returns current progress (`status: "QUEUED" | "PROCESSING" | "COMPLETED" | "PARTIAL" | "FAILED" | "CANCELLED"`, `concurrency: 3`) and completed row results.

### `GET /api/v1/enrichment/jobs/{jobId}/events`
Subscribes to a real-time Server-Sent Events (SSE) stream (`text/event-stream`) providing live visual observability of concurrent worker execution.

* **Event Types**:
  - `init`: Handshake containing `jobId`, `datasetName`, `concurrency`, `totalRows`, and current status.
  - `execution-event`: Granular row lifecycle events with payload:
    ```json
    {
      "jobId": "d8dfa97a-b1e2-4103-ac71-013252a3a824",
      "rowId": "row-0",
      "rowIndex": 0,
      "entity": "Jasveer Singh",
      "status": "PROCESSING",
      "stage": "RESEARCH",
      "workerId": "worker-1",
      "message": "Searching public sources & discovering references...",
      "timestamp": "2026-09-06T09:20:00.123Z",
      "metadata": { "sourcesCount": 4, "confidence": 0.92 }
    }
    ```
  - `job-completed`: Terminal event indicating batch execution is finished with final duration and statistics.

### `POST /api/v1/enrichment/jobs/{jobId}/cancel`
Cancels an active or queued batch enrichment job. Currently executing rows complete while queued row futures are cancelled.

* **Response `200 OK`**: Returns the updated job state with `status: "CANCELLED"`.

### `POST /api/v1/enrichment/single`
Synchronously enriches a single row record and persists the entity snapshot.

* **Request Body**:
  ```json
  {
    "row": { "Name": "Linus Torvalds", "Profile": "https://github.com/torvalds" },
    "columnMapping": { "nameColumn": "Name", "urlColumn": "Profile" },
    "entityType": "PERSON",
    "userRequirement": "Find primary open-source projects created"
  }
  ```

### `GET /api/v1/entities`
Lists catalog of saved entities from MySQL with pagination.

* **Query Params**: `page=0&size=20`
* **Response `200 OK`**: Paginated array of entity summaries (`displayName`, `canonicalUrl`, `entityType`, `attributesCount`, `sourcesCount`).

### `GET /api/v1/entities/{id}`
Retrieves full details of a saved entity including all multi-source corroborated attributes and source URLs.

---

## 7. V2 Unified Intelligence & Ingestion Endpoints

V2 introduces modular dataset ingestion with format sniffing, data quality profiling, and AI requirement planning.
All V2 endpoints support the `X-Correlation-ID` header for distributed tracing and return RFC 7807 Problem Detail responses with enriched metadata (`code`, `requestId`, `timestamp`, `details`).

### `POST /api/v2/datasets/upload` (`dataset-service :9743`)
Uploads a CSV or Excel (`.xlsx`) dataset. Sniffs delimiters (comma, semicolon, tab), parses quotes and multiline cells, and isolates malformed rows without failing the batch.

* **Headers**: `Content-Type: multipart/form-data`, `Accept: application/json`
* **Form Data**: `file` (binary CSV or XLSX)
* **Response `200 OK`**:
  ```json
  {
    "datasetName": "team_members.csv",
    "headers": ["Full Name", "Company", "Role", "LinkedIn Profile"],
    "rows": [
      {
        "Full Name": "Alice Smith",
        "Company": "Stripe",
        "Role": "Staff Engineer",
        "LinkedIn Profile": "https://www.linkedin.com/in/alicesmith"
      }
    ],
    "malformedRows": [],
    "totalRowCount": 1
  }
  ```

### `POST /api/v2/datasets/profile` (`dataset-service :9743`)
Analyzes raw dataset schema, detects column roles, calculates field completeness, highlights conflicting identity values, calculates an overall quality score (0–100), and provides recommended column mappings.

* **Request Body**:
  ```json
  {
    "dataset": {
      "datasetName": "team_members.csv",
      "headers": ["Full Name", "Company", "Role", "LinkedIn Profile"],
      "rows": [...]
    }
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "datasetName": "team_members.csv",
    "totalRows": 1,
    "overallQualityScore": 95,
    "qualityExplanation": "High quality: 100% average field completeness, 0 data conflicts detected.",
    "entityColumn": "Full Name",
    "columnProfiles": [
      {
        "columnName": "Full Name",
        "detectedRole": "NAME",
        "completenessPercentage": 100.0,
        "sampleValues": ["Alice Smith"],
        "uniqueCount": 1
      },
      {
        "columnName": "LinkedIn Profile",
        "detectedRole": "LINKEDIN_URL",
        "completenessPercentage": 100.0,
        "sampleValues": ["https://www.linkedin.com/in/alicesmith"],
        "uniqueCount": 1
      }
    ],
    "recommendedMappings": {
      "nameColumn": "Full Name",
      "urlColumn": "LinkedIn Profile",
      "organizationColumn": "Company",
      "roleColumn": "Role"
    },
    "conflicts": []
  }
  ```

### `POST /api/v2/ai/requirements/plan` (`ai-intelligent-service :9742`)
Translates unstructured user requirements into a structured, executable `EnrichmentPlan`. Identifies canonical field keys, plans extraction strategies (`PRIMARY_SOURCE`, `SECONDARY_DISCOVERY`, `WEB_SEARCH`), and re-uses existing dataset columns to prevent unnecessary web lookups.

* **Request Body**:
  ```json
  {
    "userRequirement": "Extract employer, educational background, and technical skills",
    "entityType": "PERSON",
    "datasetColumns": ["Full Name", "Company", "LinkedIn Profile"]
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "userGoalSummary": "Enrich 2 requested attributes for PERSON",
    "plannedFields": [
      {
        "fieldKey": "education",
        "displayName": "Education",
        "description": "Educational background and degrees",
        "strategy": "SECONDARY_DISCOVERY",
        "existingInDataset": false,
        "priority": 1
      },
      {
        "fieldKey": "skills",
        "displayName": "Skills",
        "description": "Technical skills and expertise",
        "strategy": "SECONDARY_DISCOVERY",
        "existingInDataset": false,
        "priority": 2
      }
    ],
    "inferredEntityType": "PERSON",
    "estimatedSourcesCount": 3
  }
  ```

### `POST /api/v1/ai/extract` (`ai-intelligent-service :9742`)
Extracts structured factual attributes from source text using Spring AI (Gemini 2.5 Flash) with fallback to deterministic heuristic extraction. Verifies that every returned fact is anchored by an exact verbatim quote from the text.

* **Headers**: `Content-Type: application/json`, `Accept: application/json`
* **Request Body**:
  ```json
  {
    "entityName": "Alex Chen",
    "entityType": "PERSON",
    "sourceUrl": "https://example.com/team/alex",
    "textContent": "Alex Chen is a Staff Infrastructure Engineer at CloudScale Inc located in Seattle, Washington.",
    "targetFields": ["role", "organization", "location"]
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "entityName": "Alex Chen",
    "sourceUrl": "https://example.com/team/alex",
    "facts": {
      "role": {
        "value": "Staff Infrastructure Engineer",
        "exactQuote": "Alex Chen is a Staff Infrastructure Engineer",
        "confidenceScore": 0.95
      },
      "organization": {
        "value": "CloudScale Inc",
        "exactQuote": "at CloudScale Inc",
        "confidenceScore": 0.95
      },
      "location": {
        "value": "Seattle, Washington",
        "exactQuote": "located in Seattle, Washington",
        "confidenceScore": 0.90
      }
    },
    "modelUsed": "gemini-2.5-flash",
    "executionTimeMs": 142
  }
  ```

### `POST /api/v2/ai/objective/parse` (`ai-intelligent-service :9742`)
Parses an open-ended research goal into structured dimensional targets and focus areas.

* **Headers**: `Content-Type: application/json`, `Accept: application/json`
* **Request Body**:
  ```json
  {
    "objective": "Find senior backend engineers with extensive distributed systems and Kubernetes experience"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "rawObjective": "Find senior backend engineers with extensive distributed systems and Kubernetes experience",
    "targetRole": "Senior Backend Engineer",
    "requiredSkills": ["Distributed Systems", "Kubernetes"],
    "seniorityLevel": "SENIOR",
    "domainFocus": "Cloud Infrastructure"
  }
  ```

### `POST /api/v2/ai/profile/assess` (`ai-intelligent-service :9742`)
Executes multi-dimensional profile assessment against an objective, computing role alignment, experience depth, and verified skill scores.

* **Headers**: `Content-Type: application/json`, `Accept: application/json`
* **Request Body** (`ProfileAssessmentRequest`):
  ```json
  {
    "displayName": "Linus Torvalds",
    "entityType": "PERSON",
    "canonicalUrl": "https://github.com/torvalds",
    "objective": "Find Linux kernel maintainers with C systems programming background",
    "attributes": {
      "currentRole": "Principal Fellow",
      "organization": "Linux Foundation"
    },
    "sources": [
      {
        "url": "https://github.com/torvalds",
        "title": "Linus Torvalds",
        "snippet": "Creator of Linux and Git"
      }
    ]
  }
  ```
* **Response `200 OK`** (`ProfileAssessmentResponse`):
  ```json
  {
    "displayName": "Linus Torvalds",
    "overallMatchScore": 0.98,
    "fitCategory": "STRONG_MATCH",
    "dimensionalScores": {
      "roleAlignment": 1.0,
      "skillOverlap": 0.96,
      "domainRelevance": 1.0
    },
    "executiveSummary": "World-leading systems architect and original creator of Linux kernel and Git.",
    "highlightedStrengths": ["Kernel development", "Distributed version control", "C systems programming"],
    "potentialGaps": [],
    "confidenceTier": "HIGH",
    "assessedAt": "2026-09-06T10:00:00Z"
  }
  ```



