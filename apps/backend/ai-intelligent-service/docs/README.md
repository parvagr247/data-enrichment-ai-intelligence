# AI Intelligent Service

## 1. Why This Service Exists
The **AI Intelligent Service** isolates all Large Language Model (LLM) interactions and structured factual extractions into a standalone microservice.
* **Decoupling Latency & Failure Domains**: LLM calls (via Spring AI / Google GenAI Gemini) have high latency and variable failure modes (rate limits, context window limits, schema errors). Keeping them separate prevents research orchestration and database operations from being impacted.
* **Pluggable Intelligence**: Allows updating prompt strategies, models, or fallback engines without redeploying the research pipeline.

---

## 2. API Specifications

### `POST /api/v1/ai/extract`
Extracts grounded, verifiable facts about a target entity from raw text.

* **Request Body** (`ExtractionRequest`):
  ```json
  {
    "entityName": "Spring Boot",
    "entityType": "FRAMEWORK",
    "sourceUrl": "https://spring.io/projects/spring-boot",
    "textContent": "Spring Boot makes it easy to create stand-alone...",
    "targetFields": ["description", "organization", "license"]
  }
  ```
* **Response Body** (`ExtractionResponse`):
  ```json
  {
    "entityName": "Spring Boot",
    "sourceUrl": "https://spring.io/projects/spring-boot",
    "facts": {
      "description": {
        "value": "Makes it easy to create stand-alone, production-grade Spring based Applications",
        "exactQuote": "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications.",
        "confidenceScore": 0.95
      }
    },
    "modelUsed": "gemini-1.5-flash",
    "executionTimeMs": 624
  }
  ```

---

## 3. Service Flow (Short)

```
[POST /api/v1/ai/extract]
   │
   ▼
ExtractionController.extract(request)
   │
   ▼
SpringAiExtractionService.extractFacts(request)
   ├── 1. Check Mock / Key: If mockMode or missing API key -> Deterministic Rule Engine
   ├── 2. Live Extraction: Format strict JSON prompt -> ChatModel.call()
   │      └── Parse JSON -> Run Zero-Hallucination Guard
   └── 3. Fallback: If Spring AI fails or throws -> extractDeterministically(request)
```

---

## 4. Critical & Non-Trivial Code (Why Only This)

### A. Zero-Hallucination Verification Guard
* **Location**: [`SpringAiExtractionService.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiExtractionService.java#L128-L132)
```java
// Zero-hallucination guard: quote must actually appear in the text
if (request.textContent().toLowerCase(Locale.ROOT).contains(quote.toLowerCase(Locale.ROOT).trim())) {
    facts.put(entry.getKey(), new ExtractedFact(val, quote, score));
}
```
* **Why only this**: LLMs frequently hallucinate plausible-sounding quotes. Any extracted fact whose `exactQuote` cannot be verified verbatim in the original source text is rejected immediately.

### B. Transparent Deterministic Fallback Engine
* **Location**: [`SpringAiExtractionService.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiExtractionService.java#L65-L74)
```java
try {
    facts = extractViaSpringAi(request);
    modelUsed = properties.model();
} catch (Exception ex) {
    log.warn("Spring AI extraction failed ({}), falling back to deterministic extraction", ex.getMessage());
    facts = extractDeterministically(request);
    modelUsed = "deterministic-fallback";
}
```
* **Why only this**: If the external Gemini API is rate-limited, unreachable, or returns invalid JSON, the service catches the exception and falls back to a regex sentence-parsing heuristic. This guarantees the upstream research pipeline never halts due to third-party AI outages.

### C. Strict Context Window Truncation
* **Location**: [`SpringAiExtractionService.java`](file:///P:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiExtractionService.java#L112)
```java
request.textContent().length() > 6000 ? request.textContent().substring(0, 6000) : request.textContent()
```
* **Why only this**: Caps input text to the first 6,000 characters before sending to the model. Prevents prompt bloat, keeps latency predictable (<1s), and avoids unnecessary token consumption while preserving the most relevant header and lead-paragraph content.
