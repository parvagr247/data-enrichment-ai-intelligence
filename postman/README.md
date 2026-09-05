# Postman API Collection: Data Enrichment Platform

This directory contains the official production Postman collection for validating, testing, and interacting with all microservices in the Data Enrichment AI Intelligence Platform.

---

## 1. Collection Overview

The collection file is located at:
`postman/data-enrichment-ai-intelligence.postman_collection.json`

It features **43 requests across 7 structured folders**, providing complete test coverage across synchronous pipelines, background jobs, fact extraction, entity persistence, and RFC 7807 error handling.

### Included Service Folders & Capabilities

1. **`01 - Health & Actuator` (6 requests)**
   - Research Service Health (`GET :9741/actuator/health`) & Info (`GET :9741/actuator/info`)
   - AI Intelligence Service Health (`GET :9742/actuator/health`) & Info (`GET :9742/actuator/info`)
   - Dataset Persistence Service Health (`GET :9743/actuator/health`) & Info (`GET :9743/actuator/info`)

2. **`02 - Research Service (Synchronous)` (5 requests)**
   - `Execute Research - Repository Target`: Full target with URL, name, and entityType (captures `{{entityId}}`)
   - `Discovery-First Research`: Target with name only, URL omitted (derives deterministic URN identity)
   - `URL-Only Research`: Target with URL only, name omitted (infers canonical display name)
   - `Person Research`: Individual person profile (Jane Doe) triggering professional fact extraction
   - `Research with Execution Metadata`: Attaches arbitrary client metadata, tags, and tracking properties

3. **`03 - Research Service (Asynchronous Jobs)` (4 requests)**
   - `Submit Async Research Job`: Enqueues research in background thread (returns 202 Accepted, captures `{{jobId}}`)
   - `Poll Job Status by Job ID`: Queries lifecycle status (`SUBMITTED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`) and captures `{{asyncEntityId}}`
   - `Submit Async Discovery Job`: Enqueues discovery-first job without initial URL
   - `404 - Nonexistent Research Job`: Asserts 404 Not Found contract on unknown job identifiers

4. **`04 - Research Service (Validation & Errors)` (5 requests)**
   - `Validation Error - Missing Both URL and Name`: Asserts RFC 7807 400 Bad Request
   - `Validation Error - Malformed URL Format`: Rejects invalid URL schemes/structures
   - `Validation Error - Invalid EntityType Enum`: Rejects invalid enum values with allowed options
   - `Error Simulation - Upstream Search 502 Bad Gateway`: Simulates upstream search provider outage
   - `Error Simulation - Upstream Search 504 Gateway Timeout`: Simulates upstream search provider timeout

5. **`05 - AI Intelligent Service` (8 requests)**
   - `Extract Facts - Software Repository`: Fact extraction with verbatim citation
   - `Extract Facts - Person Profile`: Professional role and bio fact extraction
   - `Extract Facts - Organization Profile`: Company headquarters and mission extraction
   - `Extract Facts - Custom Target Fields`: User-specified attribute keys
   - `Validation Error - Missing entityName`: Asserts 400 Bad Request
   - `Validation Error - Missing textContent`: Asserts 400 Bad Request
   - `Validation Error - Blank textContent`: Asserts 400 Bad Request on whitespace
   - `Validation Error - Missing sourceUrl`: Asserts 400 Bad Request on missing provenance URL

6. **`06 - Dataset Persistence Service` (9 requests)**
   - `Persist Enriched Entity (Verified Schema)`: Correctly formatted `attributes` map and `sources` array (captures `{{entityId}}`)
   - `Upsert / Update Existing Entity`: Updates entity snapshot and tests orphan removal
   - `Get Entity by ID`: Retrieves detailed entity record using `{{entityId}}`
   - `List All Entities`: Returns array of entity summaries with aggregate metrics
   - `List Entities Paginated`: Pagination query using `?page=0&size=5`
   - `Entity Not Found (404)`: Asserts 404 Not Found contract
   - `Validation Error - Missing entityId`: Asserts 400 Bad Request
   - `Validation Error - Missing displayName`: Asserts 400 Bad Request
   - `Validation Error - Missing canonicalUrl`: Asserts 400 Bad Request

7. **`07 - End-to-End Orchestrated Pipeline Flow` (6 requests)**
   - `E2E 01 - Gate Health Verification`: Verifies service readiness
   - `E2E 02 - Trigger Full Research Operation`: Executes synchronous research and saves `{{entityId}}`
   - `E2E 03 - Verify Auto-Persisted Snapshot in Dataset Service`: Queries `dataset-service` to confirm cross-service persistence
   - `E2E 04 - Submit Background Async Research Job`: Enqueues async job and saves `{{jobId}}`
   - `E2E 05 - Poll Background Job to Completion`: Polls job progress and captures `{{asyncEntityId}}`
   - `E2E 06 - Verify Async Enriched Entity in Dataset Service`: Confirms background worker persistence into `dataset-service`

---

## 2. Collection Variables

The collection comes pre-configured with default variables matching the local Docker and development port mapping:

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `baseResearchUrl` / `researchUrl` | `http://localhost:9741` | Base URL for `research-service` |
| `baseAiUrl` / `aiUrl` | `http://localhost:9742` | Base URL for `ai-intelligent-service` |
| `baseDatasetUrl` / `datasetUrl` | `http://localhost:9743` | Base URL for `dataset-service` |
| `jobId` | *(dynamically captured)* | Auto-saved from async job submissions |
| `entityId` | *(dynamically captured)* | Auto-saved from research / persistence responses |
| `asyncEntityId` | *(dynamically captured)* | Auto-saved from completed background jobs |

---

## 3. How to Use in Postman

1. Open Postman.
2. Click **Import** (top left) and select `postman/data-enrichment-ai-intelligence.postman_collection.json`.
3. Start the microservices (e.g. via `docker compose -f infrastructure/docker/docker-compose-dev-all.yml up` or local Maven).
4. Run requests individually, or open the **Collection Runner** to execute the entire sequence. The test scripts dynamically pass IDs (`jobId`, `entityId`, `asyncEntityId`) downstream.

---

## 4. Running via Newman (CLI Automated Testing)

You can run the entire collection headlessly from the command line using Newman:

```bash
# Install newman globally
npm install -g newman

# Run the entire collection
newman run postman/data-enrichment-ai-intelligence.postman_collection.json --reporters cli

# Run specifically the End-to-End Orchestrated Pipeline Flow folder
newman run postman/data-enrichment-ai-intelligence.postman_collection.json --folder "07 - End-to-End Orchestrated Pipeline Flow" --reporters cli
```
