# Technical Concepts 11–20 — AI Research & Evidence

> Status: Draft  
> Version: 0.1  
> Last Updated: 2026-09-04

## Purpose

This document details the operational mechanics connecting external web research, Spring AI tool calling, and structured evidence extraction. Large Language Models (LLMs) possess no native ability to browse the web or access real-time information; they operate strictly over the context supplied to them. This document outlines the architectural boundaries governing how our application equips the model with deterministic tools to acquire facts with verifiable provenance.

### Conceptual Architecture

```text
                    Research Agent
                          │
              ┌───────────┼───────────┐
              │           │           │
              ▼           ▼           ▼
         Search Tool   URL Tool    API Tool
              │           │           │
              └───────────┬───────────┘
                          │
                          ▼
                      Evidence
                          │
                          ▼
                  Structured Output
```

---

### 11. Search vs Direct URL Fetching

**What it is**  
Direct URL fetching retrieves the raw textual content of a specific, known HTTP endpoint. Web search queries a search engine index to discover candidate URLs, page titles, and excerpted snippets matching keyword queries.

**Why this project cares**  
A seed URL may be uninformative, redirect to a generic landing page, or be blocked by bot protections. Direct fetching alone cannot discover secondary profiles, recent publications, or corroborating corporate pages. Search enables multi-source discovery.

**Relevant to**  
`Autonomous research → Multi-source discovery → URL candidate generation`

**What I need to understand**  
* **Search result ≠ verified evidence**: Search engine snippets are compressed, index-derived summaries; they serve as discovery pointers, not complete factual citations.
* Direct fetching retrieves the actual document text, which is required for verbatim quotation and primary evidence extraction.
* A robust research strategy uses direct fetching for primary seed validation and search queries for corroboration and gap-filling.

**Authoritative reference**  
* [W3C: Architecture of the World Wide Web, Volume One](https://www.w3.org/TR/webarch/)

---

### 12. Retrieval-Augmented Generation (RAG) vs Web Research

**What it is**  
Traditional RAG queries a pre-indexed, static vector database over internal documents using semantic similarity embeddings. Autonomous web research dynamically interacts with live external search engines and web pages, actively discovering unknown resources at runtime.

**Why this project cares**  
Our enrichment targets arbitrary, dynamic entities across the open web. We cannot pre-embed the entire internet into a local vector store. The engine must actively research, retrieve, and synthesize fresh external data per entity.

**Relevant to**  
`Entity ingestion → Autonomous research planning → Dynamic evidence compilation`

**What I need to understand**  
* Traditional RAG assumes a closed corpus with pre-computed vector indexes; web research operates over an open, unindexed, and unpredictable web corpus.
* Vector similarity does not guarantee factual truth or source authority; our engine relies on explicit URL provenance and deterministic tool queries rather than nearest-neighbor vector chunks.
* In-memory context windows require tight snippet filtering to prevent injecting megabytes of irrelevant HTML into the LLM prompt.

**Authoritative reference**  
* [Lewis et al., 2020: Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks](https://arxiv.org/abs/2005.11401) (NeurIPS 2020)
* [Spring AI: Retrieval Augmented Generation (RAG) Overview](https://docs.spring.io/spring-ai/reference/api/vectordbs.html)

---

### 13. AI Tool Calling / Function Calling

**What it is**  
Tool calling (or function calling) is an LLM capability where the model evaluates a user prompt against a list of tool definitions (JSON schemas describing name, description, and parameters). Instead of answering directly, the model emits a structured JSON payload requesting that a specific tool be invoked with specified arguments.

**Why this project cares**  
Tool calling is the exact mechanism that gives our single research agent deterministic agency. The agent uses tools like `fetchUrl` and `searchWeb` to actively gather empirical data from the web.

**Relevant to**  
`Spring AI ChatClient → Tool invocation → External research execution`

**What I need to understand**  
* The LLM **never executes the tool itself**: It merely generates a structured intent request containing function name and arguments.
* Tool definitions must contain crystal-clear `@Description` annotations; the LLM uses descriptions to decide when and how to invoke each tool.
* Tool calling replaces brittle regex parsing of model text with typed, schema-validated parameters.

**Authoritative reference**  
* [Spring AI: Tool Calling / Function Calling Reference](https://docs.spring.io/spring-ai/reference/api/tools.html)

---

### 14. Agent Loop: Reason → Tool → Observe → Continue

**What it is**  
The agent loop (popularized by the ReAct pattern) is an iterative execution cycle:
1. **Reason**: Model inspects goals and existing context.
2. **Tool**: Model emits a tool call request.
3. **Observe**: Application executes the tool and injects the output into context.
4. **Continue**: Model reasons over the observation, choosing to call another tool or finalize the answer.

**Why this project cares**  
Single-pass prompts fail on complex research. An agent needs multiple iterative steps: inspect seed URL ➔ observe missing company details ➔ execute search query ➔ observe secondary page ➔ compile final result.

**Relevant to**  
`Research orchestration → Multi-step retrieval → Iterative evidence gathering`

**What I need to understand**  
* Every loop iteration consumes model tokens and wall-clock time; enforce a strict maximum iteration limit (e.g., 3–5 tool calls per entity) to prevent runaway execution.
* The application maintains the conversation history across iterations, appending user prompts, assistant tool calls, and tool observation messages.
* When the model receives sufficient evidence, it terminates the loop by returning the final structured enrichment payload.

**Authoritative reference**  
* [Yao et al., 2023: ReAct: Synergizing Reasoning and Acting in Language Models](https://arxiv.org/abs/2210.03629) (ICLR 2023)
* [Spring AI: ChatClient Advisors and Multi-turn Conversations](https://docs.spring.io/spring-ai/reference/api/chatclient.html)

---

### 15. Spring AI ChatClient

**What it is**  
`ChatClient` is the fluent, high-level client API introduced in Spring AI. It provides a builder-based interface to configure system prompts, user inputs, default parameters, advisors, and tool bindings across diverse LLM providers.

**Why this project cares**  
`ChatClient` is the primary interface our backend uses to execute AI extraction and evaluation. It decouples our application code from vendor-specific SDKs (OpenAI, Anthropic, Ollama, Google GenAI).

**Relevant to**  
`Backend service layer → Spring AI configuration → LLM invocation`

**What I need to understand**  
* Create `ChatClient` instances using `ChatClient.Builder` injected via Spring Boot auto-configuration.
* Default system prompts (e.g., "You are an evidence extraction engine...") can be defined at client construction to enforce zero-hallucination guardrails across all calls.
* Supports both blocking/synchronous execution (`.call()`) and streaming execution (`.stream()`); our initial synchronous engine uses `.call()`.

**Authoritative reference**  
* [Spring AI: ChatClient API Reference](https://docs.spring.io/spring-ai/reference/api/chatclient.html)

---

### 16. Spring AI Tool Calling

**What it is**  
Spring AI's implementation of tool calling allows standard Java methods (annotated with `@Tool` or registered as Spring `@Bean` definitions of type `Function<Request, Response>`) to be exposed directly to the AI model.

**Why this project cares**  
This enables our backend to provide Java methods (`fetchUrl`, `searchWeb`) directly to the LLM. Spring AI handles JSON schema generation, parameter deserialization, Java method execution, and result formatting automatically.

**Relevant to**  
`Research engine tools → Java method binding → Spring AI execution`

**What I need to understand**  
* Tools can be registered per-request using `.tools(myTools)` on the `ChatClient` fluent API.
* Parameter types and descriptions are automatically inferred from Java record fields and docstrings.
* Tool methods must be fast, deterministic, and handle internal exceptions gracefully so that error messages return as tool observations rather than crashing the loop.

**Authoritative reference**  
* [Spring AI: Tool Calling Guide](https://docs.spring.io/spring-ai/reference/api/tools.html)

---

### 17. Tool Execution Ownership: Application vs Model

**What it is**  
The architectural distinction between **decision ownership** and **execution ownership**. The model owns the decision of *what tool to request and with what parameters*. The application host environment owns *the execution environment, network access, security boundaries, and data validation*.

**Why this project cares**  
Confusing these boundaries creates severe security and reliability vulnerabilities. The LLM must never be allowed to execute arbitrary network calls, file system writes, or bypass security rules.

**Relevant to**  
`Security boundaries → HTTP request isolation → Tool execution safety`

**What I need to understand**  
* The application can inspect, validate, sanitize, or reject any tool call requested by the model before executing it.
* Restrict tool capabilities to permitted actions: URL fetchers must block internal IP ranges (SSRF protection against `127.0.0.1`, `169.254.169.254`).
* The model operates strictly as a reasoning module; the host application enforces all rate limits, timeouts, and access controls.

**Authoritative reference**  
* [Spring AI: Tool Execution Lifecycle](https://docs.spring.io/spring-ai/reference/api/tools.html)

---

### 18. Structured Output

**What it is**  
Structured output is the process of guiding an LLM to emit responses that conform strictly to a predefined schema (such as a JSON object matching a Java record) rather than unstructured, conversational prose.

**Why this project cares**  
Our enrichment pipeline requires strongly-typed, verifiable records (`EnrichmentResult`, `EvidenceTuple`) that downstream controllers, databases, and export tools can serialize and parse deterministically.

**Relevant to**  
`AI extraction → JSON response parsing → Java DTO mapping`

**What I need to understand**  
* Spring AI provides `StructuredOutputConverter` and fluent `.entity(EnrichmentResult.class)` to bind LLM output directly to Java records.
* Under the hood, Spring AI appends schema formatting instructions to the prompt or leverages provider-native JSON Schema mode.
* Output converters validate incoming JSON; malformed syntax triggers deserialization errors that must be caught or retried.

**Authoritative reference**  
* [Spring AI: Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)

---

### 19. JSON Schema

**What it is**  
JSON Schema is a declarative, vocabulary-based specification for annotating and validating the structure, constraints, and data types of JSON documents.

**Why this project cares**  
Our generic entity and enrichment result definitions rely on JSON Schema (Draft 2020-12) to define the structure of evidence tuples, confidence enums, and entity attributes across all supported categories (`PERSON`, `ORGANIZATION`, `PRODUCT`, etc.).

**Relevant to**  
`Schema validation → Contract definition → Model response constraints`

**What I need to understand**  
* Specifies required fields, allowable data types (`string`, `object`, `array`), and enum constraints (`HIGH`, `MEDIUM`, `LOW`, `UNKNOWN`).
* Spring AI generates JSON Schema definitions directly from Java class/record reflection to instruct the LLM.
* Schema validation should be enforced before accepting any extracted result into the domain layer.

**Authoritative reference**  
* [JSON Schema Specification: Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-core.html)
* [RFC 8259: The JavaScript Object Notation (JSON) Data Interchange Format](https://www.rfc-editor.org/rfc/rfc8259) (STD 90)

---

### 20. Evidence and Provenance

**What it is**  
Provenance is information about the entities, activities, and people involved in producing a piece of data, providing an audit trail for assessing its quality, reliability, and trustworthiness. In our engine, evidence is the pairing of a factual assertion with its verbatim source text, source URL, retrieval timestamp, and confidence rating.

**Why this project cares**  
An LLM asserting a job title or skill without evidence is untrustworthy. Downstream users require verifiable citations. If evidence cannot be retrieved, the value must explicitly be set to `UNKNOWN`.

**Relevant to**  
`Evidence tuple compilation → Zero-hallucination enforcement → Result export`

**What I need to understand**  
* **LLM text ≠ evidence**: A model asserting "Jane Doe is CTO" is not evidence. Evidence is: "Verbatim snippet: 'Jane Doe serves as Chief Technology Officer' retrieved from 'https://example.com/team' at 2026-09-04T12:00:00Z".
* Every enriched field must trace directly back to one or more evidence items (field-level provenance).
* The W3C PROV standard models provenance as Entities (attributes), Activities (research/tool calls), and Agents (research engine/tools).

**Authoritative reference**  
* [W3C: PROV-DM: The PROV Data Model](https://www.w3.org/TR/prov-dm/)
* [W3C: PROV-O: The PROV Ontology](https://www.w3.org/TR/prov-o/)
