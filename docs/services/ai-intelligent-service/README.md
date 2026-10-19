# AI Intelligent Service

The **AI Intelligent Service** (`com.subdual.ai_intelligent_service`) encapsulates all Large Language Model (LLM) interactions, prompt templating, and structured factual extraction into a dedicated, resilient microservice.

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
│   │   └── dto/ (AIEnrichmentResult, EnrichmentSynthesisRequest, InputCleansingRequest, etc.)
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
│   ├── exception/
│   │   ├── AiErrorClassifier.java                # Error categorization (Rate-limit, Timeout, etc.)
│   │   └── GlobalExceptionHandler.java           # RFC 7807 problem details handler
│   ├── normalization/
│   │   └── AiOutputNormalizer.java               # JSON markdown fence stripper & sanitizer
│   ├── filter/
│   │   └── CorrelationIdFilter.java              # MDC correlation ID logging filter
│   └── dto/
│       └── FactEvidenceDto.java                  # Inter-capability fact evidence transfer
│
└── configuration/                                # Spring configuration properties
    ├── AiProperties.java                         # gemini-2.5-flash model, timeout & fallback flags
    └── WebCorsConfiguration.java                 # Web MVC CORS policy configuration
```

---

## 4. AI-Generated Intelligence vs. Deterministic Fallback

| Capability | Live Spring AI Mode (`SpringAiIntelligence`) | Deterministic Fallback Mode (`DeterministicAiIntelligence`) |
| :--- | :--- | :--- |
| **Provider** | Google GenAI (`gemini-3.5-flash-lite`) | Pure Java Regex, NLP token heuristics, static dictionaries |
| **Trigger Condition** | Default production when `GEMINI_API_KEY` is present | Triggered on missing API key, HTTP 429 quota exhaustion, network timeout (>15s), or invalid JSON |
| **Grounded Facts** | Semantic contextual extraction with verbatim quote matching | Exact keyword and pattern extraction from text; uncorroborated fields set to `UNKNOWN` |
| **Requirement Parsing**| Interprets nuance, implied synonyms, and domain contexts | Keyword tokenization and static category matching |
| **Profile Scoring** | Multi-dimensional reasoning, qualitative summary, strategy | Deterministic arithmetic scoring based on detected attribute counts |

For in-depth details on prompt templates, the zero-hallucination verification guard, and error classification, see **[AI Intelligent Service Internals](internals.md)**.
