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
