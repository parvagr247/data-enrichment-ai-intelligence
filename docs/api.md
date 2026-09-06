# API Reference

This document provides a comprehensive catalog of all REST API endpoints provided by the three Spring Boot microservices.

---

## 1. Services Overview

| Service | Base URL | Primary Role |
| :--- | :--- | :--- |
| **`research-service`** | `http://localhost:9741` | Research execution, web scraping, and evidence collection |
| **`ai-intelligent-service`** | `http://localhost:9742` | Requirement interpretation, input cleansing, and grounded fact extraction |
| **`dataset-service`** | `http://localhost:9743` | Batch enrichment jobs, row-level tracking, and MySQL entity persistence |

---

## 2. Research Service (`:9741`)

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

## 3. AI Intelligent Service (`:9742`)

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

## 4. Dataset Service (`:9743`)

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
Polls status, progress counters, and row-level results of a batch enrichment job.

* **Response `200 OK`**: Returns current progress and completed row results.

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

## 5. V2 Unified Intelligence & Ingestion Endpoints

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


