# Frontend Application

The **Frontend Application** (`apps/frontend`) is a Next.js 15 web client providing an interactive interface for dataset ingestion, schema profiling, real-time concurrent observability, and multi-tab evidence verification.

---

## 1. Product Workflow

```
Tabular File (CSV / XLSX)
       ↓
1. Upload & In-Browser Parsing (SheetJS)
       ↓
2. Preview & Schema Profiling (Auto-detect Name, URL, Organization, Role)
       ↓
3. Confirm Mappings & Enter Requirement Prompt
       ↓
4. Bounded Concurrent Enrichment (Real-time SSE Dashboard)
       ↓
5. Inspect Grounded Evidence (Multi-Tab Modal: Quotes, Sources, Conflicts)
       ↓
6. Non-Destructive Export (Download enriched CSV/XLSX)
```

---

## 2. Key Architecture & Features

### A. Client-Side SheetJS Parsing
* Parses CSV and Excel (`.xlsx`) files directly inside the user's browser using `xlsx` (SheetJS).
* The backend server never handles raw binary file uploads or multipart temporary files on disk.

### B. Real-Time Observability Dashboard
* Subscribes to Server-Sent Events (`/api/v1/enrichment/jobs/{jobId}/events`) via the browser's native `EventSource`.
* Renders `LiveExecutionDashboard` with dynamic active worker cards (`worker-1`, `worker-2`, `worker-3`), live duration counters, and stage badges (`RESEARCH`, `AI_EXTRACTION`, `PERSISTENCE`).
* Includes automatic interval polling fallback if the SSE connection is interrupted by proxy timeouts.

### C. Incremental Evidence Modal Inspection
* Completed rows immediately populate the table and the "Recently Completed" list.
* Users can open the multi-tab `EvidenceDetailModal` to inspect verbatim quotes, source URLs, and confidence tiers *while remaining workers are actively processing remaining rows*.

### D. Non-Destructive Export
* Appends new `Enriched_<attribute>`, `Canonical_Url`, and `Enrichment_Status` columns to the spreadsheet.
* Strictly preserves all original column names, order, and values.

---

## 3. Running Locally

```bash
# From apps/frontend directory
npm install

# Start Next.js development server
npm run dev

# Build for production
npm run build
```

The frontend runs on `http://localhost:3000` and routes all API requests through the API Gateway at `http://localhost:9738`.
