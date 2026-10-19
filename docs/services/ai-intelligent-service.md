# AI Intelligent Service

The **AI Intelligent Service** (`com.subdual.ai_intelligent_service`) encapsulates all Large Language Model (LLM) interactions, prompt templating, and structured factual extraction into a dedicated, resilient microservice.

---

## Table of Contents
- [1. Core Responsibilities](#1-core-responsibilities)
- [2. Key Capabilities & Endpoints](#2-key-capabilities--endpoints)
- [3. Simplified 4-Package Architecture](#3-simplified-4-package-architecture)
- [4. Zero-Hallucination Verification Guard](#4-zero-hallucination-verification-guard)
- [5. Context Truncation & Latency Management](#5-context-truncation--latency-management)
- [6. Prompt Template Architecture](#6-prompt-template-architecture)
- [7. Error Classification & Resilience](#7-error-classification--resilience)
- [8. Deterministic Fallback Engines](#8-deterministic-fallback-engines)

---

## 1. Core Responsibilities

* **Decoupled Latency & Failure Domains**: External LLM calls have high latency (1–5 seconds) and volatile failure modes (rate limits, context limits, schema parsing errors). Isolating them prevents database connections and web crawling pipelines from stalling.
* **Zero-Hallucination Verification Guard**: Guarantees that every extracted fact is grounded in an exact verbatim quote from the source document. If evidence is absent, the value is marked `UNKNOWN`.
* **Pluggable Architecture & Transparent Fallback**: Functions seamlessly in both online (Google Gemini) and offline/mock modes using a deterministic heuristic fallback engine.
* **Multi-Domain Intelligence**: Provides specialized AI reasoning for enrichment, extraction, enrichment planning, and dimensional profile assessments.

---

## 2. Key Capabilities & Endpoints

| Method | Path | Description | Domain |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/ai/extract` | Extracts grounded factual tuples from raw text with exact verbatim quotes. | `extraction` |
| `POST` | `/api/v1/ai/clean` | Cleans noisy input seeds (normalizes names, strips emojis, parses compound roles/titles). | `enrichment` |
| `POST` | `/api/v1/ai/requirement` | Translates natural language requirements into structured target fields. | `enrichment` |
| `POST` | `/api/v1/ai/enrich` | Core fact synthesis and enrichment pipeline endpoint used by `research-service`. | `enrichment` |
| `POST` | `/api/v2/ai/requirements/plan` | Generates a structured field-by-field research strategy plan against existing columns. | `planning` |
| `POST` | `/api/v2/ai/objective/parse` | Parses open-ended research objectives into structured dimensions. | `profile` |
| `POST` | `/api/v2/ai/profile/assess` | Multi-dimensional profile assessment and fit scoring. | `profile` |
| `GET` | `/actuator/health` | Service health status probe (`UP`). | Public |

---

## 3. Simplified 4-Package Architecture

The codebase is organized into four clean, top-level packages:

```
com.subdual.ai_intelligent_service
├── AiIntelligentServiceApplication.java
│
├── capability/                                   # Domain business capabilities
│   ├── enrichment/                               # Capability 1: Input cleansing & fact synthesis
│   │   ├── EnrichmentAiController.java           # POST /requirement, POST /clean, POST /enrich
│   │   ├── EnrichmentAIService.java              # Enrichment service contract
│   │   ├── SpringAiEnrichmentService.java        # Spring AI + fallback implementation
│   │   ├── DeterministicEnrichmentHelper.java    # Regex & heuristic offline cleansing
│   │   └── dto/ (AIEnrichmentResult, EnrichmentSynthesisRequest, InputCleansingRequest)
│   │
│   ├── extraction/                               # Capability 2: Grounded verbatim fact extraction
│   │   ├── ExtractionController.java             # POST /api/v1/ai/extract
│   │   ├── ExtractionService.java                # Fact extraction contract
│   │   ├── SpringAiExtractionService.java        # Spring AI verbatim extraction engine
│   │   ├── DeterministicExtractionHelper.java    # Deterministic regex pattern extractor
│   │   └── model/ (ExtractionRequest, ExtractionResponse, ExtractedFact)
│   │
│   ├── planning/                                 # Capability 3: Strategy planning
│   │   ├── EnrichmentPlanningController.java     # POST /api/v2/ai/requirements/plan
│   │   ├── EnrichmentPlanningService.java        # Planning contract
│   │   ├── DefaultEnrichmentPlanningService.java # Planning orchestrator
│   │   ├── DefaultPlanGenerator.java             # Strategy generator (Primary, Secondary, Search)
│   │   ├── RequirementNormalizer.java            # Keyword & requirement normalization
│   │   ├── RequirementValidator.java             # Input validation & sanity checks
│   │   └── model/ (EnrichmentPlan, PlannedField, FieldSourceStrategy, EnrichmentRequirement)
│   │
│   └── profile/                                  # Capability 4: Dimensional profile assessment
│       ├── ProfileAssessmentController.java      # POST /objective/parse, POST /profile/assess
│       ├── ProfileAssessmentEngine.java          # Assessment engine contract
│       ├── SpringAiProfileAssessmentEngine.java  # Spring AI dimensional evaluator
│       ├── DeterministicProfileAssessmentEngine.java # Offline arithmetic scoring fallback
│       ├── ProfileExtractionHelper.java          # Profile attribute aggregation
│       ├── dto/ (ObjectiveParseRequest, ProfileAssessmentRequest, ProfileAssessmentResponse)
│       └── model/ (ResearchProfile, ObjectiveAssessment, RecommendedApproach, ResearchFinding)
│
├── ai/                                           # Centralized AI infrastructure & prompts
│   ├── AiPromptExecutor.java                     # Spring AI ChatClient execution coordinator
│   ├── AiResponseValidator.java                  # Verbatim quote grounding & schema validator
│   ├── AiExecutionMetrics.java                   # Token telemetry & latency metrics
│   ├── PromptTemplates.java                      # Externalized prompt templates
│   └── PromptTemplateService.java                # Dynamic template interpolation service
│
├── shared/                                       # Cross-cutting error handling & normalization
│   ├── exception/AiErrorClassifier.java          # Error categorization (Rate-limit, Timeout, etc.)
│   ├── exception/GlobalExceptionHandler.java     # RFC 7807 problem details handler
│   ├── normalization/AiOutputNormalizer.java     # JSON markdown fence stripper & sanitizer
│   ├── filter/CorrelationIdFilter.java           # MDC correlation ID logging filter
│   └── dto/FactEvidenceDto.java                  # Inter-capability fact evidence transfer
│
└── configuration/                                # Spring configuration properties
    ├── AiProperties.java                         # gemini-2.5-flash model, timeout & fallback flags
    └── WebCorsConfiguration.java                 # Web MVC CORS policy configuration
```

---

## 4. Zero-Hallucination Verification Guard

Generative LLMs excel at synthesizing unstructured text, but they can hallucinate plausible-sounding claims when information is ambiguous or missing.

### Algorithmic Guardrail
In `AiResponseValidator` and `SpringAiExtractionService`:
1. The LLM is instructed via system prompts that every extracted attribute value **must** be accompanied by an `exactQuote` string extracted verbatim from the input text.
2. When the structured JSON response is received, the service programmatically validates the quote against the source document:

```java
public boolean isVerbatimGrounded(String sourceText, String exactQuote) {
    if (exactQuote == null || exactQuote.isBlank() || sourceText == null) {
        return false;
    }
    String normalizedSource = sourceText.toLowerCase(Locale.ROOT);
    String normalizedQuote = exactQuote.toLowerCase(Locale.ROOT).trim();
    return normalizedSource.contains(normalizedQuote);
}
```

3. If the quote is not present verbatim, the candidate fact is discarded. The field value is set to `"UNKNOWN"` with confidence `"UNKNOWN"`.
4. Downstream research and dataset consumers are guaranteed that no hallucinated facts enter the platform database.

---

## 5. Context Truncation & Latency Management

To ensure predictable response times and prevent context window overflow:
* Input documents are capped at **6,000 characters** before prompt interpolation.
* Boilerplate navigation and non-informational headers are stripped upstream in `research-service`.
* Capping context maintains sub-second inference latencies and reduces Google GenAI token costs by up to 80%.

---

## 6. Prompt Template Architecture

Prompts are externalized and managed through `PromptTemplateService` (`com.subdual.ai_intelligent_service.ai`):
* **`PromptTemplates.java`**: Houses structured prompt templates for requirement parsing, entity cleansing, fact extraction, and multi-dimensional profile assessments.
* **Structured Output Schema**: Uses Spring AI's structured output converters to enforce JSON responses conforming to target Java records (`ExtractionResponse`, `AIEnrichmentResult`, `ProfileAssessmentResponse`).

---

## 7. Error Classification & Resilience (`AiErrorClassifier`)

Network timeouts, provider quotas, and model deprecations are classified by `AiErrorClassifier` into structured diagnostic categories:

| Error Category | Typical Root Cause | System Response |
| :--- | :--- | :--- |
| **`RATE_LIMIT`** | HTTP 429 quota exhaustion on Gemini API. | Immediate fallback to deterministic heuristic engine; logs warning. |
| **`UNSUPPORTED_MODEL`** | Provider retired model (e.g. HTTP 404 on deprecated endpoints). | Fast fallback to deterministic engine; alerts operator. |
| **`TIMEOUT`** | Network call exceeded bounded 15-second limit. | Cancels thread and switches to deterministic engine. |
| **`AUTHENTICATION_FAILURE`** | Missing or invalid `GEMINI_API_KEY`. | Safe redacted logging; transitions to offline deterministic mode. |
| **`STRUCTURED_PARSING_FAILURE`** | LLM returned malformed JSON or markdown fences. | Triggers secondary cleanup normalizer; if unparseable, triggers fallback. |

---

## 8. Deterministic Fallback Engines

When external AI is unavailable, the service delegates to pure-Java heuristic engines:

1. **`DeterministicEnrichmentHelper`**:
   - Cleans names using regex patterns for honorifics, emojis, and noise.
   - Maps natural language requirement keywords into standard entity field definitions.
2. **`DeterministicExtractionHelper`**:
   - Searches input text for regex patterns matching target fields (emails, titles, phone numbers, known technology stacks, company names).
   - Generates exact substring quotes corresponding to matches.
3. **`DeterministicProfileAssessmentEngine`**:
   - Calculates profile fit scores based on attribute completeness and matching keyword frequencies.
   - Assigns priority tiers (`HIGH`, `MEDIUM`, `LOW`) using deterministic heuristics.
