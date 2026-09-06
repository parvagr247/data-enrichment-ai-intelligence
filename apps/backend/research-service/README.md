# Research Service

The **Research Service** coordinates autonomous web discovery, content fetching, boilerplate cleaning, evidence compilation, and multi-source corroboration for target entities.

---

## 1. Why This Service Exists

* **Core Principle**: *"Research produces evidence."*
* **Autonomous Exploration**: LLMs cannot browse the web directly. This service treats input URLs and names as research seeds, queries search engines, fetches web pages, and compiles corroborating factual evidence before triggering AI extraction.
* **Provider Decoupling**: Abstracts web search behind `SearchProvider` interfaces (Tavily with offline `MockSearchProvider` fallback).

---

## 2. Key Capabilities & Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/research` | Executes synchronous end-to-end research for an entity. |
| `POST` | `/api/v1/research/jobs` | Submits an asynchronous research job for an entity. |
| `GET` | `/api/v1/research/jobs/{jobId}` | Polls the status of an asynchronous research job. |
| `GET` | `/actuator/health` | Service health status probe (`UP`). |

---

## 3. Architecture & Non-Trivial Implementation

### A. Provider Strategy & Adapter Pattern
* `SearchProvider` interface enables switching between live Tavily search and offline mock responses without pipeline modifications.

### B. Defensive Web Crawling Guardrails
* `DefaultWebContentFetcher` enforces strict 5,000ms socket timeouts, 500KB content caps, and Jsoup HTML boilerplate removal.
* Social media and bot-blocking fallback: if direct scraping returns HTTP 999 or 403, the service falls back to extracting evidence from search engine snippets.

### C. Multi-Source Corroboration Engine
* `EvidenceMerger` evaluates claims across multiple sources:
  - Boosts confidence (`LOW` &rarr; `MEDIUM` &rarr; `HIGH`) when independent sources agree.
  - Flags contradictory claims (`conflictDetected = true`) with a descriptive audit trail.

---

For complete API contracts, see [API Reference](../../../docs/api.md).  
For local development, see [Development Guide](../../../docs/development.md).
