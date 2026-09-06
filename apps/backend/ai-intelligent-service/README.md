# AI Intelligent Service

The **AI Intelligent Service** isolates all Large Language Model (LLM) interactions, prompt templating, and structured factual extraction into a dedicated Spring Boot microservice.

---

## 1. Why This Service Exists

* **Decoupling Latency & Failure Domains**: LLM calls (via Spring AI / Google GenAI Gemini) have high latency and variable failure modes (rate limits, context window limits, schema errors). Keeping them separate prevents research orchestration and database operations from being impacted.
* **Pluggable Intelligence**: Allows updating prompt strategies, models, or fallback engines without redeploying the research pipeline.
* **Guaranteed Reliability**: Implements a transparent deterministic fallback engine so the platform functions continuously during provider outages.

---

## 2. Key Capabilities & Endpoints

| Method | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/ai/extract` | Extracts grounded facts from raw text with exact verbatim quotes. |
| `POST` | `/api/v1/ai/clean` | Cleans noisy input seeds (normalizes names, titles, URLs). |
| `POST` | `/api/v1/ai/requirement` | Parses natural language requirements into structured target fields. |
| `POST` | `/api/v1/ai/enrich` | Core fact extraction pipeline endpoint used by `research-service`. |
| `POST` | `/api/v2/ai/objective/parse` | Parses open-ended research objectives into dimensional targets. |
| `POST` | `/api/v2/ai/profile/assess` | Multi-dimensional profile assessment against an objective. |
| `GET` | `/actuator/health` | Service health status probe (`UP`). |

---

## 3. Architecture & Non-Trivial Implementation

### A. Zero-Hallucination Verification Guard
* **Location**: `SpringAiExtractionService.java`
* Every extracted fact candidate must supply an `exactQuote`. The service validates that the quote appears verbatim within the source text before returning the fact:
```java
if (request.textContent().toLowerCase(Locale.ROOT).contains(quote.toLowerCase(Locale.ROOT).trim())) {
    facts.put(entry.getKey(), new ExtractedFact(val, quote, score));
}
```

### B. Transparent Deterministic Fallback Engine
* **Location**: `SpringAiExtractionService.java`
* If Spring AI throws an exception (due to HTTP 429, missing API key, network timeout, or invalid JSON), execution automatically falls back to `DeterministicProfileAssessmentEngine` using regex and NLP heuristics.

### C. Context Truncation
* Input text is capped at 6,000 characters before prompt interpolation, preventing prompt bloat and maintaining predictable latencies (<1s).

---

For complete API contracts, see [API Reference](../../../docs/api.md).  
For local development, see [Development Guide](../../../docs/development.md).
