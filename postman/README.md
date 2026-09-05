# Postman API Collection: Data Enrichment Platform

This directory contains the official Postman collection for validating and interacting with all microservices in the Data Enrichment AI Intelligence Platform.

---

## 1. Collection Overview

The collection file is located at:
`postman/data-enrichment-ai-intelligence.postman_collection.json`

### Included Service Folders & Capabilities

1. **`01 - Health & Actuator`**
   - Research Service Health (`GET :9741/actuator/health`)
   - AI Intelligence Service Health (`GET :9742/actuator/health`)
   - Dataset Persistence Service Health (`GET :9743/actuator/health`)
2. **`02 - AI Extraction Service`**
   - Extract Facts from Raw Content (`POST :9742/api/v1/ai/extract`)
   - Validation Error Contract (`POST :9742/api/v1/ai/extract` with missing fields returning 400 ProblemDetail)
3. **`03 - Dataset Persistence Service`**
   - Persist Enriched Entity Snapshot (`POST :9743/api/v1/entities`) - captures `{{entityId}}`
   - List All Enriched Entities (`GET :9743/api/v1/entities`)
   - Get Entity Details by ID (`GET :9743/api/v1/entities/{{entityId}}`)
   - Filter Entities by Entity Type (`GET :9743/api/v1/entities?entityType=ORGANIZATION`)
   - 404 Not Found Contract (`GET :9743/api/v1/entities/nonexistent-id`)
4. **`04 - Synchronous Research Pipeline`**
   - Full Research with URL and Name (`POST :9741/api/v1/research`)
   - Discovery-First Research with Name only (`POST :9741/api/v1/research`) - generates canonical URN
   - Research with URL only (`POST :9741/api/v1/research`)
5. **`05 - Asynchronous Research Jobs`**
   - Submit Async Job (`POST :9741/api/v1/research/jobs`) - auto-captures `{{jobId}}`
   - Poll Job Status by ID (`GET :9741/api/v1/research/jobs/{{jobId}}`) - auto-captures `{{entityId}}` on completion
6. **`06 - Error Handling & Validation`**
   - 400 Bad Request when both URL and Name are omitted
   - 400 Bad Request for malformed URL
   - 404 Not Found for nonexistent job UUID

---

## 2. Collection Variables

The collection is pre-configured with default variables targeting local docker/development environments:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `researchUrl` | `http://localhost:9741` | Base URL for `research-service` |
| `aiUrl` | `http://localhost:9742` | Base URL for `ai-intelligent-service` |
| `datasetUrl` | `http://localhost:9743` | Base URL for `dataset-service` |
| `jobId` | *(dynamically captured)* | Auto-saved from async job submissions |
| `entityId` | *(dynamically captured)* | Auto-saved from research / persistence responses |

---

## 3. How to Use in Postman

1. Open Postman.
2. Click **Import** (top left) and select `postman/data-enrichment-ai-intelligence.postman_collection.json`.
3. Verify that the services are running (e.g. via `docker compose -f infrastructure/docker/docker-compose-dev-all.yml up` or local Maven).
4. Run requests individually, or use the **Collection Runner** to execute the entire test sequence. The post-request test scripts will automatically pass dynamic IDs (`jobId`, `entityId`) between dependent requests.

---

## 4. Running via Newman (CLI Automated Testing)

You can run the collection directly from the command line using Newman:

```bash
# Install newman if not already installed
npm install -g newman

# Run the collection against running services
newman run postman/data-enrichment-ai-intelligence.postman_collection.json --reporters cli
```
