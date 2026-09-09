# API Endpoints Catalog

This document is the authoritative endpoint catalog for all REST and Server-Sent Events (SSE) interfaces exposed by the backend microservices.

---

## Service Port Reference

| Service | Internal Port | Gateway Path Prefix | Primary Ingress Protocol |
| :--- | :--- | :--- | :--- |
| **`auth-service`** | `9739` | `/api/v1/auth/**` | HTTP REST |
| **`dataset-service`** | `9743` | `/api/v1/enrichment/**`, `/api/v1/entities/**`, `/api/v2/datasets/**` | HTTP REST + SSE |
| **`research-service`** | `9741` | `/api/v1/research/**`, `/api/v1/sources/**` | HTTP REST |
| **`ai-intelligent-service`** | `9742` | `/api/v1/ai/**`, `/api/v2/ai/**` | HTTP REST |
| **`api-gateway`** | `8080` / `9738` | Reverse proxy to all above | HTTP / SSE Ingress |

---

## 1. Auth Service (`:9739`)

Base path: `/api/v1/auth`

### 1.1 Register User Account
* **Endpoint**: `POST /api/v1/auth/register`
* **Access**: Public
* **Description**: Creates a new user account with BCrypt-hashed password and returns a signed HMAC-SHA256 JWT token.
* **Request Body**:
  ```json
  {
    "name": "Alex Mercer",
    "email": "alex@example.com",
    "password": "strongPassword123"
  }
  ```
* **Response `201 Created`**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjMWY3MmE0NC04ZDRlLTRmM2ItODIxOS1jNmU4YmIyZTk3YWEiLCJlbWFpbCI6ImFsZXhAZXhhbXBsZS5jb20iLCJpYXQiOjE3MjU2NDQxMzAsImV4cCI6MTcyNTcyNzMzMH0.signature",
    "user": {
      "id": "c1f72a44-8d4e-4f3b-8219-c6e8bb2e97aa",
      "name": "Alex Mercer",
      "email": "alex@example.com",
      "createdAt": "2026-09-06T20:15:30Z"
    }
  }
  ```
* **Errors**:
  - `400 Bad Request`: Validation failure (blank fields or malformed email).
  - `409 Conflict`: Email address already registered.

### 1.2 User Login
* **Endpoint**: `POST /api/v1/auth/login`
* **Access**: Public
* **Description**: Verifies user credentials against BCrypt hash and returns a signed JWT.
* **Request Body**:
  ```json
  {
    "email": "alex@example.com",
    "password": "strongPassword123"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjMWY3MmE0NC04ZDRlLTRmM2ItODIxOS1jNmU4YmIyZTk3YWEiLCJlbWFpbCI6ImFsZXhAZXhhbXBsZS5jb20iLCJpYXQiOjE3MjU2NDQxMzAsImV4cCI6MTcyNTcyNzMzMH0.signature",
    "user": {
      "id": "c1f72a44-8d4e-4f3b-8219-c6e8bb2e97aa",
      "name": "Alex Mercer",
      "email": "alex@example.com",
      "createdAt": "2026-09-06T20:15:30Z"
    }
  }
  ```
* **Errors**:
  - `401 Unauthorized`: Invalid email or password.

### 1.3 Get Current User Profile
* **Endpoint**: `GET /api/v1/auth/me`
* **Access**: Authenticated (Requires Bearer token or Gateway forwarded `X-User-Id`)
* **Headers**: `Authorization: Bearer <token>`
* **Response `200 OK`**:
  ```json
  {
    "id": "c1f72a44-8d4e-4f3b-8219-c6e8bb2e97aa",
    "name": "Alex Mercer",
    "email": "alex@example.com",
    "createdAt": "2026-09-06T20:15:30Z"
  }
  ```

---

## 2. Dataset Service (`:9743`)

The Dataset Service manages batch enrichment jobs, real-time SSE streaming, dataset ingestion, and MySQL persistence.

### 2.1 Submit Batch Enrichment Job
* **Endpoint**: `POST /api/v1/enrichment/jobs`
* **Access**: Authenticated / Multi-tenant (Scoped to `X-User-Id`)
* **Description**: Submits a batch dataset of rows for concurrent asynchronous research and AI extraction.
* **Request Body**:
  ```json
  {
    "datasetName": "q3_leads.csv",
    "userRequirement": "Extract company headquarters, tech stack, and primary products",
    "defaultEntityType": "ORGANIZATION",
    "columnMapping": {
      "nameColumn": "Company",
      "urlColumn": "Website",
      "organizationColumn": null,
      "roleColumn": null
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
    "jobId": "job-d8e27c1a-9f4a-4d7a-8b1e-2c9f7a6b5c4d",
    "status": "PROCESSING",
    "totalRows": 2,
    "completedRows": 0,
    "failedRows": 0,
    "pendingRows": 2,
    "results": []
  }
  ```

### 2.2 Get Batch Job Status & Results
* **Endpoint**: `GET /api/v1/enrichment/jobs/{jobId}`
* **Access**: Scoped to owner `userId` (IDOR protected)
* **Response `200 OK`**:
  ```json
  {
    "jobId": "job-d8e27c1a-9f4a-4d7a-8b1e-2c9f7a6b5c4d",
    "datasetName": "q3_leads.csv",
    "status": "COMPLETED",
    "totalRows": 2,
    "completedRows": 2,
    "failedRows": 0,
    "pendingRows": 0,
    "concurrency": 3,
    "createdAt": "2026-09-10T01:30:00Z",
    "completedAt": "2026-09-10T01:30:15Z",
    "results": [
      {
        "rowId": "row-0",
        "rowIndex": 0,
        "input": { "Company": "Stripe", "Website": "https://stripe.com" },
        "entityName": "Stripe",
        "canonicalUrl": "https://stripe.com",
        "status": "SUCCESS",
        "attributes": {
          "headquarters": {
            "value": "San Francisco, California and Dublin, Ireland",
            "evidenceSnippet": "Dual-headquartered in San Francisco and Dublin",
            "confidence": "HIGH",
            "sourceUrl": "https://stripe.com/about"
          }
        },
        "sources": [
          { "url": "https://stripe.com/about", "title": "About Stripe", "domain": "stripe.com" }
        ],
        "executionDurationMs": 2340
      }
    ]
  }
  ```

### 2.3 Subscribe to Real-Time Job Events (SSE)
* **Endpoint**: `GET /api/v1/enrichment/jobs/{jobId}/events`
* **Protocol**: Server-Sent Events (`text/event-stream`)
* **Events**:
  * `init`: Handshake containing job metadata and concurrency.
  * `execution-event`: Granular row state transitions:
    ```json
    {
      "jobId": "job-d8e27c1a-...",
      "rowId": "row-0",
      "rowIndex": 0,
      "entity": "Stripe",
      "status": "PROCESSING",
      "stage": "RESEARCH",
      "workerId": "worker-1",
      "message": "Crawling official domain and discovering sources...",
      "timestamp": "2026-09-10T01:30:02.100Z",
      "metadata": { "sourcesCount": 3 }
    }
    ```
  * `job-completed`: Terminal event indicating all rows finished.

### 2.4 Cancel Active Batch Job
* **Endpoint**: `POST /api/v1/enrichment/jobs/{jobId}/cancel`
* **Access**: Scoped to owner `userId`
* **Response `200 OK`**:
  ```json
  {
    "jobId": "job-d8e27c1a-...",
    "status": "CANCELLED"
  }
  ```

### 2.5 Single Row Synchronous Enrichment
* **Endpoint**: `POST /api/v1/enrichment/single`
* **Description**: Synchronously enriches a single row record and persists the entity snapshot.
* **Request Body**:
  ```json
  {
    "row": { "Name": "Linus Torvalds", "Profile": "https://github.com/torvalds" },
    "columnMapping": { "nameColumn": "Name", "urlColumn": "Profile" },
    "entityType": "PERSON",
    "userRequirement": "Find primary open-source projects created"
  }
  ```
* **Response `200 OK`**: Single row enrichment result object.

### 2.6 Persisted Entity Catalog
* **List Entities**: `GET /api/v1/entities?page=0&size=20`
  * Returns paginated summaries (`displayName`, `canonicalUrl`, `entityType`, `attributesCount`, `sourcesCount`).
* **Get Entity Details**: `GET /api/v1/entities/{id}`
  * Returns full entity detail including all multi-source corroborated attributes, evidence quotes, and discovered sources.

### 2.7 V2 Dataset Ingestion & Profiling
* **Upload Dataset**: `POST /api/v2/datasets/upload`
  * **Headers**: `Content-Type: multipart/form-data`
  * **Payload**: Form field `file` (CSV or XLSX binary)
  * **Description**: Performs delimiter sniffing, multiline handling, and malformed row isolation.
* **Profile Dataset**: `POST /api/v2/datasets/profile`
  * **Request Body**: Raw dataset headers and rows.
  * **Description**: Detects column semantic roles, completeness %, identity conflicts, and quality score (0–100).

---

## 3. Research Service (`:9741`)

The Research Service runs multi-source web crawlers, extracts text, handles redirects, and resolves canonical URLs.

### 3.1 Synchronous Entity Research
* **Endpoint**: `POST /api/v1/research`
* **Headers**: `Content-Type: application/json`
* **Request Body**:
  ```json
  {
    "url": "https://github.com/spring-projects/spring-boot",
    "name": "Spring Boot",
    "organization": "VMware / Broadcom",
    "role": "Framework",
    "entityType": "REPOSITORY",
    "userRequirement": "Extract primary language, license, and core purpose"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "status": "COMPLETED",
    "entityId": "a1b2c3d4-...",
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

### 3.2 Asynchronous Research Job Execution
* **Submit Job**: `POST /api/v1/research/jobs` (Returns `202 Accepted` with `jobId`)
* **Poll Job**: `GET /api/v1/research/jobs/{jobId}` (Returns `status: "PENDING" | "RUNNING" | "COMPLETED" | "FAILED"`)

### 3.3 Source Inspection
* **Endpoint**: `GET /api/v1/sources/{sourceId}`
* **Description**: Retrieves source metadata and raw crawl status for a discovered web resource.

---

## 4. AI Intelligent Service (`:9742`)

The AI Intelligent Service provides Spring AI LLM grounding (Gemini 2.5 Flash), requirement planning, and deterministic heuristic cleansing.

### 4.1 Fact Extraction (`/api/v1/ai/extract`)
* **Endpoint**: `POST /api/v1/ai/extract`
* **Description**: Extracts structured factual attributes backed by strict verbatim quotes using Spring AI, with automatic fallback to deterministic heuristic extraction.
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

### 4.2 Seed Cleansing & Entity Detection (`/api/v1/ai/clean`)
* **Endpoint**: `POST /api/v1/ai/clean`
* **Description**: Strips noisy honorifics, corporate legal suffixes, tracking UTM parameters, and normalizes identity seeds.
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

### 4.3 Requirement Interpretation (`/api/v1/ai/requirement`)
* **Endpoint**: `POST /api/v1/ai/requirement`
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

### 4.4 V2 AI Requirement Planning (`/api/v2/ai/requirements/plan`)
* **Endpoint**: `POST /api/v2/ai/requirements/plan`
* **Description**: Plans field strategies (`PRIMARY_SOURCE`, `SECONDARY_DISCOVERY`, `WEB_SEARCH`) and matches against existing dataset columns to eliminate redundant web crawls.
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

### 4.5 V2 Objective Parsing (`/api/v2/ai/objective/parse`)
* **Endpoint**: `POST /api/v2/ai/objective/parse`
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

### 4.6 V2 Multi-Dimensional Profile Assessment (`/api/v2/ai/profile/assess`)
* **Endpoint**: `POST /api/v2/ai/profile/assess`
* **Request Body**:
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
* **Response `200 OK`**:
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
    "assessedAt": "2026-09-10T02:00:00Z"
  }
  ```
