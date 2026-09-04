# Technical Concepts 01–10 — Web & Retrieval Foundations

> Status: Draft  
> Version: 0.1  
> Last Updated: 2026-09-04

## Purpose

This document provides a concise technical reference on web protocols, resource identifiers, and retrieval mechanics. In this project, an input URL serves strictly as a research seed; programmatic HTTP fetching behaves fundamentally differently from interactive web browsers. Understanding these foundational web standards is essential for building a robust, polite, and realistic research engine.

---

### 01. URI vs URL

**What it is**  
A Uniform Resource Identifier (URI) is a standardized string of characters that identifies an abstract or physical resource. A Uniform Resource Locator (URL) is a specific subset of URI that identifies a resource by specifying its primary access mechanism (e.g., `https`) and network location.

**Why this project cares**  
Raw tabular datasets supply diverse seed strings: bare domains, tracking-polluted URLs, or platform identifiers. The engine must parse, normalize, and validate these seeds into deterministic canonical URLs to generate stable entity IDs (e.g., via SHA-256) and prevent duplicate research cycles.

**Relevant to**  
`Seed URL ingestion → Normalization → Canonical Entity ID generation`

**What I need to understand**  
* Every URL is a URI, but not every URI is a resolvable URL (e.g., `urn:isbn:0451450523` is an identifier without a network locator).
* Query parameters often contain ephemeral tracking tokens (e.g., `utm_source`, `ref`) that must be stripped during canonicalization.
* Normalization requires consistent scheme lowercasing, default port removal, trailing slash standardization, and path percent-encoding normalization.

**Authoritative reference**  
* [RFC 3986: Uniform Resource Identifier (URI): Generic Syntax](https://www.rfc-editor.org/rfc/rfc3986) (obsoleted RFC 2396)

---

### 02. HTTP Request/Response Model

**What it is**  
The Hypertext Transfer Protocol (HTTP) is a stateless, client-server application-level protocol. A client initiates a TCP/TLS connection, transmits a request message (method, target URI, headers, optional payload), and the server returns a response message (status code, headers, payload).

**Why this project cares**  
The research engine acts as an HTTP client fetching public evidence pages. Every page inspection consumes network resources, is subject to remote server latency, and requires explicit connection management to avoid socket exhaustion.

**Relevant to**  
`Autonomous research → Polite HTTP fetching → Evidence extraction`

**What I need to understand**  
* HTTP transactions are strictly synchronous per request-response pair over standard connection pools.
* Remote servers may terminate connections, throttle traffic, or close idle sockets unexpectedly.
* Programmatic fetching does not maintain interactive user state, session cookies, or client-side storage across disparate domains.

**Authoritative reference**  
* [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110) (STD 97, obsoleted RFC 7230 and RFC 7231)

---

### 03. HTTP Methods and Status Codes

**What it is**  
HTTP methods define the desired action to be performed on a resource (`GET`, `HEAD`, `POST`). Status codes are three-digit integers categorized into informational (1xx), successful (2xx), redirection (3xx), client error (4xx), and server error (5xx).

**Why this project cares**  
The engine executes `GET` and `HEAD` requests to inspect external sources. It must interpret remote status codes accurately: a `200 OK` allows evidence extraction, a `301/308` triggers canonical redirect tracking, a `403/401` halts unauthorized access, and a `429` triggers backoff.

**Relevant to**  
`HTTP client execution → Status evaluation → Error handling`

**What I need to understand**  
* `GET` and `HEAD` requests must remain safe and idempotent; the engine never issues state-altering verbs (`POST`, `PUT`, `DELETE`) to external target domains.
* `403 Forbidden` and `401 Unauthorized` mean the resource is legally or technically inaccessible without authentication; the engine marks the field `UNKNOWN` and never attempts bypasses.
* `404 Not Found` and `410 Gone` indicate the seed resource no longer exists and must not be retried indefinitely.

**Authoritative reference**  
* [RFC 9110: HTTP Semantics — Section 9 (Methods) & Section 15 (Status Codes)](https://www.rfc-editor.org/rfc/rfc9110)

---

### 04. HTTP Headers and Content Types

**What it is**  
Headers are colon-separated name-value pairs exchanging metadata between client and server. The `Content-Type` header communicates the MIME media type of the payload (e.g., `text/html`, `application/json`, `application/pdf`).

**Why this project cares**  
Before downloading multi-megabyte payloads or attempting HTML text extraction, the engine must inspect the `Content-Type` header. Extracting text from binary media, images, or large archives without dedicated parsers degrades performance and corrupts evidence snippets.

**Relevant to**  
`Header inspection → MIME filtering → Text snippet extraction`

**What I need to understand**  
* Client requests should declare clear intent using `Accept: text/html, application/xhtml+xml, text/plain` and a descriptive, polite `User-Agent`.
* Inspect `Content-Type` and `Content-Length` before consuming the entire response body; ignore non-textual MIME types (e.g., `image/*`, `video/*`, `application/zip`).
* Character encoding (e.g., `charset=utf-8`) must be parsed from headers or HTML `<meta>` tags to prevent Mojibake string corruption.

**Authoritative reference**  
* [RFC 9110: HTTP Semantics — Section 6 (Headers) & Section 8.3 (Media Type)](https://www.rfc-editor.org/rfc/rfc9110)

---

### 05. HTTPS / TLS at a Conceptual Level

**What it is**  
Transport Layer Security (TLS) is a cryptographic protocol providing confidentiality, integrity, and server authentication over TCP. HTTPS is standard HTTP layered over an encrypted TLS connection (typically port 443).

**Why this project cares**  
Virtually all legitimate target websites and APIs enforce HTTPS. The engine's HTTP client must perform standard TLS handshakes, validate X.509 certificate chains against trusted truststores, and support modern TLS versions (TLS 1.2, TLS 1.3).

**Relevant to**  
`HTTP client configuration → TLS handshakes → Secure socket establishment`

**What I need to understand**  
* Expired certificates, self-signed certificates, or domain mismatches cause TLS handshake failures (`SSLHandshakeException`); never disable certificate verification in production.
* Server Name Indication (SNI) allows virtual hosting over HTTPS; modern Java HTTP clients handle SNI automatically.
* TLS negotiation incurs connection latency (RTT overhead); connection pooling and keep-alive reuse mitigate repeated handshakes.

**Authoritative reference**  
* [RFC 8446: The Transport Layer Security (TLS) Protocol Version 1.3](https://www.rfc-editor.org/rfc/rfc8446) (obsoleted RFC 5246)

---

### 06. DNS and Domain Resolution

**What it is**  
The Domain Name System (DNS) is a hierarchical, distributed naming system that translates human-readable hostnames (e.g., `example.com`) into routable IP addresses (IPv4/IPv6).

**Why this project cares**  
Every external research request begins with domain resolution. Malformed seed URLs, dead domains, or misconfigured DNS servers trigger resolution errors (`UnknownHostException`) before any HTTP exchange occurs.

**Relevant to**  
`Hostname resolution → Network connectivity → Failure classification`

**What I need to understand**  
* DNS lookups block the calling thread unless resolved asynchronously or cached; the JVM maintains an internal DNS cache controlled by `networkaddress.cache.ttl`.
* Unresolvable hostnames should be classified immediately as fatal resource errors and not retried.
* Public cloud and corporate firewalls may restrict outbound UDP/TCP port 53; ensure local development and container networks permit external resolution.

**Authoritative reference**  
* [RFC 1034: Domain Names — Concepts and Facilities](https://www.rfc-editor.org/rfc/rfc1034) (STD 13)
* [RFC 1035: Domain Names — Implementation and Specification](https://www.rfc-editor.org/rfc/rfc1035) (STD 13)

---

### 07. HTML Documents vs Dynamically Rendered Web Applications

**What it is**  
A static HTML document contains its complete text content directly in the initial HTTP response markup. A dynamically rendered Single Page Application (SPA, built with React, Vue, Angular) returns an empty shell (`<div id="root"></div>`) that fetches data and builds the DOM via client-side JavaScript execution.

**Why this project cares**  
Standard HTTP clients (Java `RestClient`, `HttpClient`, or cURL) execute zero JavaScript. If a target URL relies on client-side rendering, raw HTTP fetching retrieves only boilerplate scripts, resulting in zero extractable evidence.

**Relevant to**  
`DOM parsing → Evidence extraction → Headless browser boundary decisions`

**What I need to understand**  
* **Browser visibility does not equal programmatic accessibility**: What a user sees in Chrome is the post-execution DOM, not the raw HTTP payload received by our backend.
* For the initial phase, the engine relies on raw HTTP fetching and search API snippets; full headless browser rendering (e.g., Playwright/Puppeteer) is deferred due to CPU and latency costs.
* If a target URL returns an empty JavaScript shell without content, the research engine marks the field `UNKNOWN` rather than failing the entire run.

**Authoritative reference**  
* [W3C / WHATWG: HTML Living Standard](https://html.spec.whatwg.org/)

---

### 08. robots.txt / Robots Exclusion Protocol

**What it is**  
The Robots Exclusion Protocol (standardized in RFC 9309) defines how automated web crawlers and clients should interact with a website. A plain text file located at `/robots.txt` specifies access rules (`Allow`, `Disallow`, `Crawl-delay`) keyed by `User-Agent`.

**Why this project cares**  
Strict adherence to `robots.txt` is an essential legal, ethical, and architectural boundary for this project. If a domain or specific path is disallowed for our client, the engine must not fetch it.

**Relevant to**  
`Pre-fetch validation → Ethical crawling compliance → Source filtering`

**What I need to understand**  
* Check `/robots.txt` before crawling or recurringly fetching paths on an unfamiliar domain; cache the parsed rules per domain.
* If an entry path is disallowed, fallback immediately to public search engine APIs rather than bypassing the directive.
* Disallow directives apply to web fetchers and crawlers; public search API indexes have already crawled permitted content according to their own agreements.

**Authoritative reference**  
* [RFC 9309: Robots Exclusion Protocol](https://www.rfc-editor.org/rfc/rfc9309) (Standardized September 2022)

---

### 09. HTTP Redirects and Canonical URLs

**What it is**  
An HTTP redirect (status codes `301`, `302`, `307`, `308`) informs the client that the requested resource resides under a different URI specified in the `Location` header. A canonical URL (often declared via `<link rel="canonical" href="...">`) identifies the authoritatively preferred URL among duplicate or mirrored pages.

**Why this project cares**  
Seed URLs frequently redirect (e.g., `http://` to `https://`, vanity URLs to internal profiles). The engine must follow redirects up to a safe limit and update the entity's `canonicalUrl` attribute to ensure evidence citations reference the permanent source.

**Relevant to**  
`Redirect handling → URL normalization → Canonical URL determination`

**What I need to understand**  
* Always enforce a strict maximum redirect hop limit (e.g., 3–5 hops) to prevent infinite redirect loops.
* A `301 Moved Permanently` or `308 Permanent Redirect` indicates the canonical seed URL should be permanently updated to the target location.
* In HTML responses, inspect `<link rel="canonical">` in the document `<head>` to detect the authoritative resource identifier.

**Authoritative reference**  
* [RFC 9110: HTTP Semantics — Section 15.4 (Redirection 3xx)](https://www.rfc-editor.org/rfc/rfc9110)
* [RFC 6596: The Canonical Link Relation](https://www.rfc-editor.org/rfc/rfc6596)

---

### 10. Timeouts, Connection Failures, and Inaccessible Resources

**What it is**  
Network operations are inherently fallible. Connection timeouts occur when a TCP handshake cannot be completed within a threshold. Read timeouts occur when a server accepts a connection but stalls while transmitting the response body. Inaccessible resources return client/server error codes or drop packets entirely.

**Why this project cares**  
Without tight, explicit timeouts, an unresponsive external website can hold Java virtual or OS threads indefinitely, exhausting resources and hanging the entire research pipeline.

**Relevant to**  
`HTTP client configuration → Thread safety → Fault-tolerant fallback`

**What I need to understand**  
* Every external HTTP call must define explicit **connection timeouts** (e.g., 2–3s) and **read/socket timeouts** (e.g., 5s).
* External failure must never crash the application; catch network exceptions (`SocketTimeoutException`, `ConnectException`) and map them cleanly to a `FAILED` or `UNKNOWN` state for that specific evidence source.
* Distinguish transient errors (gateway timeouts `504`, rate limits `429`) from permanent errors (DNS failure, `404 Not Found`, `410 Gone`).

**Authoritative reference**  
* [RFC 9110: HTTP Semantics — Section 15.5 (4xx) & Section 15.6 (5xx)](https://www.rfc-editor.org/rfc/rfc9110)
* [RFC 9293: Transmission Control Protocol (TCP)](https://www.rfc-editor.org/rfc/rfc9293) (STD 7)
