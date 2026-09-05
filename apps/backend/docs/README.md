# Backend Documentation

> **Status:** Active  
> **Last Updated:** 2026-09-05  

Welcome to the backend technical documentation for the **Data Enrichment AI Intelligence Platform**. This directory houses implementation roadmaps, architectural specifications, and deep-dive technical concept guides for the backend microservices.

---

## 📚 Documentation Index

### 1. Roadmaps & Workflows

* 🚀 **[Research Service: Target Pipeline & Next Development Workflow](RESEARCH_WORKFLOW.md)**  
  The primary technical roadmap detailing the transition from the initial Research Service API scaffold into an operational web discovery, retrieval, and enrichment pipeline. Covers the 7-phase staged delivery roadmap, Phase 1 (Web Discovery) specifications, configuration architecture, domain models, error handling, testing strategies, security boundaries, and immediate implementation checklists.

### 2. Architectural Deep-Dives & Technical Concepts

* 🌐 **[Concepts 01: HTTP Media Type Negotiation (`consumes` & `produces`)](concepts/concepts-1.md)**  
  Detailed analysis of HTTP header filters and content-negotiation guards in Spring Boot REST controllers, explaining RFC 7807 problem details, 415/406 status codes, and fail-fast validation.

---

## 🏛️ Related Global Documentation

For platform-wide architectural blueprints and setup guidelines, refer to the root documentation:

* 🏗️ **[System Architecture & ADRs](../../../docs/initial/architecture.md)**
* 🔍 **[Enrichment & Research Engine Foundations](../../../docs/initial/enrichment.md)**
* 📐 **[Domain Entity Model](../../../docs/setup/entity-model.md)**
* 🔌 **[Minimal API Design](../../../docs/setup/api-design.md)**
* 📦 **[Compact Backend Reference (Ports & Services)](../one.md)**
