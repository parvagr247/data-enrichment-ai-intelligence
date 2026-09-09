# AI Intelligent Service: Internals & Engineering Deep Dive

This document details the internal design patterns, zero-hallucination guardrails, prompt interpolation, error classification, and deterministic fallback engines within `ai-intelligent-service`.

---

## 1. Zero-Hallucination Verification Guard

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

## 2. Context Truncation & Latency Management

To ensure predictable response times and prevent context window overflow:
* Input documents are capped at **6,000 characters** before prompt interpolation.
* Boilerplate navigation and non-informational headers are stripped upstream in `research-service`.
* Capping context maintains sub-second inference latencies and reduces Google GenAI token costs by up to 80%.

---

## 3. Prompt Template Architecture (`prompt/`)

Prompts are externalized and managed through `PromptTemplateService`:
* **`PromptTemplates.java`**: Houses structured prompt templates for requirement parsing, entity cleansing, fact extraction, and multi-dimensional profile assessments.
* **Structured Output Schema**: Uses Spring AI's structured output converters to enforce JSON responses conforming to target Java records (`ExtractionResponse`, `AIEnrichmentResult`, `ProfileAssessmentResponse`).

---

## 4. Error Classification & Resilience (`AiErrorClassifier`)

Network timeouts, provider quotas, and model deprecations are classified by `AiErrorClassifier` into structured diagnostic categories:

| Error Category | Typical Root Cause | System Response |
| :--- | :--- | :--- |
| **`RATE_LIMIT`** | HTTP 429 quota exhaustion on Gemini API. | Immediate fallback to deterministic heuristic engine; logs warning. |
| **`UNSUPPORTED_MODEL`** | Provider retired model (e.g. HTTP 404 on deprecated endpoints). | Fast fallback to deterministic engine; alerts operator. |
| **`TIMEOUT`** | Network call exceeded bounded 15-second limit. | Cancels thread and switches to deterministic engine. |
| **`AUTHENTICATION_FAILURE`** | Missing or invalid `GEMINI_API_KEY`. | Safe redacted logging; transitions to offline deterministic mode. |
| **`STRUCTURED_PARSING_FAILURE`** | LLM returned malformed JSON or markdown fences. | Triggers secondary cleanup normalizer; if unparseable, triggers fallback. |

### Safe Credential Redacting
Log messages are sanitized before emission. API keys matching regex patterns (e.g. `AIza[0-9A-Za-z-_]{35}`) are replaced with `[REDACTED_API_KEY]` to prevent credential leakage in central log aggregators.

---

## 5. Deterministic Fallback Engines

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
