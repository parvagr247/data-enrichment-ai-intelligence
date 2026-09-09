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
| `POST` | `/api/v1/ai/enrich` | Core fact extraction pipeline endpoint used by `research-service`. | `enrichment` |
| `POST` | `/api/v1/ai/plan` | Generates a structured field-by-field research strategy plan. | `planning` |
| `POST` | `/api/v2/ai/objective/parse` | Parses open-ended research objectives into structured dimensions. | `profile` |
| `POST` | `/api/v2/ai/profile/assess` | Multi-dimensional profile assessment and fit scoring. | `profile` |
| `GET` | `/actuator/health` | Service health status probe (`UP`). | Public |

---

## 3. Feature-Centric Package Organization

The codebase is organized into four distinct, self-contained business domains, supported by a unified AI intelligence abstraction layer:

```
com.subdual.ai_intelligent_service
├── ai/                                           # Core AI abstraction layer
│   ├── AiIntelligence.java                       # Strategy interface
│   ├── impl/
│   │   ├── SpringAiIntelligence.java             # Live Google Gemini LLM implementation
│   │   └── DeterministicAiIntelligence.java      # Heuristic regex & rule-based fallback
│   ├── helper/
│   │   └── AiResponseValidator.java              # Output sanity & schema validation
│   └── dto/
│       └── AiExecutionMetrics.java               # Execution timing & token telemetry
│
├── enrichment/                                   # Domain 1: Ingestion cleansing & requirement parsing
│   ├── api/
│   │   ├── controller/EnrichmentAiController.java
│   │   └── dto/ (request & response)
│   └── service/
│       ├── EnrichmentAIService.java
│       ├── impl/SpringAiEnrichmentService.java
│       └── helper/DeterministicEnrichmentHelper.java
│
├── extraction/                                   # Domain 2: Grounded fact extraction
│   ├── api/
│   │   ├── controller/ExtractionController.java
│   │   └── dto/ (request & response)
│   ├── model/ExtractedFact.java
│   └── service/
│       ├── ExtractionService.java
│       ├── impl/SpringAiExtractionService.java
│       └── helper/DeterministicExtractionHelper.java
│
├── planning/                                     # Domain 3: Enrichment requirement planning
│   ├── api/
│   │   └── controller/EnrichmentPlanningController.java
│   ├── model/ (EnrichmentPlan, PlannedField, etc.)
│   └── service/
│       ├── EnrichmentPlanningService.java
│       ├── impl/DefaultEnrichmentPlanningService.java
│       └── helper/ (RequirementValidator, RequirementNormalizer, DefaultPlanGenerator)
│
├── profile/                                      # Domain 4: Multi-dimensional profile assessment
│   ├── api/
│   │   ├── controller/ProfileAssessmentController.java
│   │   └── dto/ (request & response)
│   ├── model/ (ResearchProfile, ObjectiveAssessment, etc.)
│   └── service/
│       ├── ProfileAssessmentEngine.java
│       ├── impl/SpringAiProfileAssessmentEngine.java
│       ├── impl/DeterministicProfileAssessmentEngine.java
│       └── helper/ProfileExtractionHelper.java
│
├── prompt/                                       # Externalized StringTemplate prompts
│   ├── PromptTemplates.java
│   └── PromptTemplateService.java
│
├── normalization/                                # Post-processing output normalizers
│   └── AiOutputNormalizer.java
│
├── exception/                                    # Classified error hierarchies
│   ├── AiErrorClassifier.java
│   └── GlobalExceptionHandler.java
│
└── configuration/                                # Spring AI & HTTP properties
    ├── AiProperties.java
    ├── SpringAiConfig.java
    ├── CorrelationIdFilter.java
    └── WebCorsConfiguration.java
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
