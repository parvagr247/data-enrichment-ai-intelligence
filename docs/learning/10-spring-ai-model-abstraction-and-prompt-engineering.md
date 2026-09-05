# Concept 10: Spring AI Model Abstraction, Prompt Engineering & Deterministic Fallbacks

Modern AI applications often suffer from deep vendor lock-in. Developers directly instantiate proprietary SDKs (e.g. OpenAI SDK, Google GenAI SDK) inside business services, scattering API keys, token counters, and raw string prompts throughout the domain logic. When the model deprecates, rate limits hit, or API keys fail, the entire application crashes.

This guide explains how `ai-intelligent-service` leverages **Spring AI** (`org.springframework.ai.chat.model.ChatModel`) to decouple AI model interactions from business logic, enforce structured JSON schemas via strict prompt templates, and provide seamless, zero-crash deterministic fallbacks.

---

## Why This Exists

In our platform:
1. We need LLMs to perform two distinct cognitive tasks:
   * **Requirement Interpretation**: Converting natural language requests ("Find Series A funding, founders, and tech stack") into typed attribute targets.
   * **Evidence-Grounded Synthesis**: Reading raw web evidence snippets and synthesizing verified entity attributes without hallucinating.
2. In automated CI/CD pipelines, offline local development, or environments without Google Gemini API keys, the application must still build, run tests, and produce valid enrichment results.
3. If Google Gemini throttles requests (HTTP 429) or experiences an outage (HTTP 503), the service must degrade gracefully to deterministic heuristic rules rather than throwing unhandled 500 errors to users.

---

## The Problem

A naive implementation typically has these critical flaws:
* **Vendor SDK Entanglement**: Directly calling `com.google.genai.Client` in controllers or domain services. Migrating to Anthropic Claude or local Ollama requires rewriting business services across the entire codebase.
* **Unstructured Output Hallucination**: Asking an LLM "Summarize this entity in JSON" without schema guardrails. The model returns conversational commentary ("Sure! Here is the JSON: ...") or markdown code blocks (```json ... ```) that break standard JSON deserializers.
* **Hard Dependency on External Connectivity**: If the external AI API is unreachable or unconfigured, the application fails on startup or blows up on the first user request.
* **No Preprocessing Validation**: Passing messy raw data straight to an LLM wastes expensive prompt tokens and increases the risk of prompt injection or model confusion.

---

## The Core Idea

1. **Spring AI Model Abstraction**: Spring AI provides the `ChatModel` interface. The application code only calls `chatModel.call(new Prompt(promptText))`. Whether the underlying provider is Google Gemini, OpenAI, Azure OpenAI, or Ollama is controlled entirely by Spring Boot configuration starters without changing a single line of Java code.
2. **Deterministic Preprocessing**: Before invoking any LLM, input data is preprocessed, trimmed, and normalized deterministically ([`cleanInput`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiEnrichmentService.java#L71-L96)).
3. **Structured Prompt Templates**: Prompt templates ([`PromptTemplates.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/prompt/PromptTemplates.java)) mandate rigid JSON schemas, anti-hallucination rules, and strict confidence tier definitions (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`).
4. **Resilient Dual-Mode Architecture**: The service inspects configuration on every request. If running in mock mode, with an unconfigured API key, or if an active LLM call throws an exception, the service seamlessly routes to deterministic rule-based algorithms (`interpretDeterministically`, `synthesizeDeterministically`).

---

## How This Project Uses It

```
apps/backend/ai-intelligent-service/
├── configuration/
│   └── AiProperties.java               # mockMode, temperature, maxTokens
├── prompt/
│   └── PromptTemplates.java            # Strict prompt schemas with anti-hallucination rules
└── service/
    ├── EnrichmentAIService.java        # Clean domain interface
    └── SpringAiEnrichmentService.java  # Spring AI implementation with deterministic fallbacks
```

### 1. The Spring AI Abstraction & Optional Dependency Injection

In [`SpringAiEnrichmentService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiEnrichmentService.java#L34-L46), `ChatModel` is injected as an `Optional<ChatModel>`:

```java
@Service
@Slf4j
public class SpringAiEnrichmentService implements EnrichmentAIService {

    private final ChatModel chatModel;
    private final AiProperties properties;
    private final String geminiApiKey;

    public SpringAiEnrichmentService(
            Optional<ChatModel> chatModel,
            AiProperties properties,
            @Value("${spring.ai.google.genai.api-key:mock-key}") String geminiApiKey
    ) {
        this.chatModel = chatModel != null ? chatModel.orElse(null) : null;
        this.properties = properties;
        this.geminiApiKey = geminiApiKey;
    }
}
```

* **Why `Optional<ChatModel>`**: If the Google GenAI starter cannot initialize because no API key is set in `application.yaml`, Spring doesn't abort startup. `chatModel` is simply `null`, and the service operates in deterministic offline mode.

### 2. Strict Prompt Construction

In [`PromptTemplates.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/prompt/PromptTemplates.java#L51-L100), prompt engineering enforces zero-hallucination guidelines and rigid JSON output:

```java
public static final String ENRICHMENT_SYNTHESIS_PROMPT = """
    You are a rigorous, evidence-grounded entity enrichment synthesizer.
    Synthesize verified enriched attributes for entity '%s' (type: %s, url: %s).
    
    ORIGINAL RAW INPUT: %s
    USER REQUIREMENT: %s
    TARGET FIELDS TO ENRICH: %s
    RESEARCH EVIDENCE COLLECTED FROM WEB SOURCES: %s
    
    CRITICAL ANTI-HALLUCINATION RULES:
    1. DO NOT INVENT INFORMATION.
    2. Ground EVERY claimed attribute strictly in the provided research evidence or raw input.
    3. If an evidence snippet supports a fact, quote or cite it verbatim.
    4. If no evidence supports a target field, DO NOT FABRICATE A VALUE. Add that field to "unresolvedFields".
    5. If multiple sources contradict each other, set status to "CONFLICT" and note both values.
    
    Return strictly valid JSON in the format:
    {
      "displayName": "...",
      "entityType": "...",
      "canonicalUrl": "...",
      "attributes": {
        "fieldName": {
          "field": "fieldName",
          "value": "Extracted Fact",
          "confidence": "HIGH|MEDIUM|LOW|UNKNOWN",
          "status": "VERIFIED|INFERRED|CONFLICT|UNRESOLVED",
          "sources": ["url1"],
          "evidence": "Verbatim quote or evidence excerpt"
        }
      },
      "unresolvedFields": ["..."],
      "conflicts": ["..."],
      "overallConfidence": 0.90
    }
    """;
```

### 3. Safe Parsing with Markdown Fence Cleaning

LLMs frequently wrap JSON in markdown tags (` ```json ... ``` `). In [`SpringAiEnrichmentService.java`](file:///p:/Agentic%20AI/Enrichment%20Platform/data-enrichment-ai-intelligence/apps/backend/ai-intelligent-service/src/main/java/com/subdual/ai_intelligent_service/service/SpringAiEnrichmentService.java#L365-L373), response sanitization guarantees robust Jackson parsing:

```java
private String cleanJsonBlocks(String raw) {
    if (raw == null) return "{}";
    String cleaned = raw.trim();
    if (cleaned.startsWith("```json")) {
        cleaned = cleaned.substring(7);
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.substring(3);
    }
    if (cleaned.endsWith("```")) {
        cleaned = cleaned.substring(0, cleaned.length() - 3);
    }
    return cleaned.trim();
}
```

### 4. Deterministic Offline Fallback

When `isMockMode()` is true or when an API call fails, `synthesizeDeterministically(...)` directly extracts attributes from the research evidence map without invoking the external LLM:

```java
if (!isMockMode() && chatModel != null) {
    try {
        String promptText = buildSynthesisPromptText(request);
        String responseText = chatModel.call(new Prompt(promptText)).getResult().getOutput().getText();
        AIEnrichmentResult result = parseSynthesisResponse(responseText, request, startTime);
        if (result != null) return result;
    } catch (Exception ex) {
        log.warn("Spring AI enrichment synthesis failed ({}), falling back to deterministic synthesis", ex.getMessage());
    }
}

return synthesizeDeterministically(request, startTime);
```

---

## Flow

```
Incoming Request (Requirement or Synthesis)
               │
               ▼
   Deterministic Preprocessing
   (cleanInput: trim whitespace, strip noise)
               │
               ▼
       Mock Mode or No API Key?
             /        \
       Yes  /          \ No
           /            \
          ▼              ▼
   Deterministic    Construct Prompt
   Rule Heuristics  (PromptTemplates)
          │              │
          │              ▼
          │         Invoke Spring AI
          │         chatModel.call(...)
          │              │
          │      Success / Fail?
          │       /           \
          │  Success           Fail (Exception)
          │     /               \
          │    ▼                 ▼
          │  Clean Markdown    Log Warning & Fallback
          │  ```json fences    to Deterministic Rules
          │    │                 │
          │    ▼                 │
          │  Jackson parseTree   │
          │  Validate Schema     │
          │    │                 │
          ▼    ▼                 ▼
      Return Valid Structured Enrichment Result
```

---

## Important Design Decisions

1. **Why Spring AI Over Vendor SDKs**:
   Using `spring-ai-starter-model-google-genai` binds all communication to Spring AI's `ChatModel` contract. If tomorrow we switch from Google Gemini to AWS Bedrock or OpenAI, we change one Maven dependency and a few YAML properties. Zero Java code in controllers or services changes.
2. **Defensive Post-Processing**:
   We never trust that an LLM's response string will deserialize cleanly. The `cleanJsonBlocks` method and Jackson `JsonNode` traversal inspect the tree node-by-node, providing default empty collections if the model omitted required arrays like `unresolvedFields` or `conflicts`.
3. **Deterministic First, AI as Enhancer**:
   The system never relies solely on AI reasoning for basic entity recognition. Regexes and deterministic normalizers map `role`, `company`, and `name` first. The LLM is reserved for synthesis and complex requirement interpretation, minimizing token costs and latency.

---

## Alternatives

| Approach | Why We Did Not Choose It |
| :--- | :--- |
| **Direct Google GenAI Java SDK** | Highly coupled to Google's proprietary classes. Fails to start without live Google credentials and cannot run offline tests easily. |
| **Unconstrained Free-Text Prompting** | Produces natural-language paragraphs ("Linus is the creator of Linux...") that cannot be automatically merged into tabular datasets or persisted to SQL columns. |
| **Strict Grammar Sampling (JSON Schema Mode)** | Supported on some LLM providers, but not uniformly across all models. Prompt-enforced JSON paired with robust client-side sanitization works reliably across all Spring AI providers. |

---

## Common Mistakes

1. **Passing Raw HTML to LLM Prompts**:
   Sending raw web pages with JavaScript, SVG paths, and CSS stylesheets consumes thousands of unnecessary tokens and confuses the model. Always clean HTML using Jsoup (stripping `<script>`, `<style>`, `<nav>`, cookies) before building prompts.
2. **Assuming LLMs Never Hallucinate When Told Not To**:
   Prompting "Do not lie" does not stop hallucination. The backend must independently cross-examine model outputs against the verbatim research snippets before marking facts as `HIGH` confidence.
3. **Hardcoding Prompt Strings in Service Methods**:
   Inlining 50-line prompt strings into method bodies clutters business logic and makes prompt iteration difficult. Centralize all prompts in dedicated templates (`PromptTemplates.java`).

---

## Production Considerations

* **Rate Limiting & Retries**: Production LLM providers strictly enforce Requests Per Minute (RPM) and Tokens Per Minute (TPM). Pairing Spring AI with a Resilience4j retry and circuit breaker prevents cascading thread exhaustion during peak load.
* **Token Cost Auditing**: Every prompt should log input tokens and output tokens. Monitoring token consumption per batch ensures enrichment costs remain predictable.
* **Model Version Pinning**: Never use floating tags like `gemini-pro-latest` in production. Always pin to specific versions (e.g. `gemini-1.5-flash-002`) to ensure extraction schemas remain consistent over time.

---

## What I Should Learn From This

1. **Always wrap external AI models behind an abstraction interface like Spring AI `ChatModel`.**
2. **Treat prompt engineering as a first-class software design activity: define schemas, enforce anti-hallucination rules, and sanitize markdown wrappers.**
3. **Build applications so they function deterministically even when external AI models are down, unconfigured, or throttling.**
4. **Clean and normalize input data deterministically before passing it to expensive AI models.**

---

**Previous:** [Concept 09: Multi-Service Architecture, Isolation & Cross-Service Orchestration](09-multi-service-architecture-and-orchestration.md) | **Next:** [Concept 11: Dataset Ingestion, Schema Detection & Multi-Tier Entity Normalization](11-dataset-ingestion-schema-detection-and-normalization.md)
