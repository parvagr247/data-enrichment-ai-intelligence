# Data Enrichment & Research Engine — Frontend

Next.js 15 client application providing a modern, interactive web interface for dataset ingestion, schema profiling, user-directed enrichment, real-time concurrent execution observability, and multi-tab evidence verification.

---

## 1. Product Workflow

```
CSV / XLSX Dataset
       ↓
1. Upload (Drag & drop file or 1-click sample dataset)
       ↓
2. Preview & Profile (Inspect row count, schema, and auto-detected column types)
       ↓
3. Confirm Columns & Requirements (Map Name, URL, Org, Role, and enter enrichment prompt)
       ↓
4. Bounded Concurrent Enrichment (Real-time SSE dashboard with active worker cards & timeline)
       ↓
5. Inspect Grounded Evidence (Multi-tab modal: Overview, Experience, Education, Skills, Sources)
       ↓
6. Non-Destructive Export (Download combined dataset with Enriched_* attributes as CSV or XLSX)
```

---

## 2. Key Architecture & Features

1. **Client-Side SheetJS Parsing**:
   - Parses CSV and Excel (`.xlsx`) files directly in-browser using `xlsx` (SheetJS).
   - Server never handles raw binary file uploads.

2. **Real-Time Execution Observability**:
   - Subscribes to Server-Sent Events (`/api/v1/enrichment/jobs/{jobId}/events`) via native `EventSource`.
   - Renders `LiveExecutionDashboard` with dynamic active worker cards (`worker-1`, `worker-2`, `worker-3`), live duration counters, stage badges (`RESEARCH`, `AI_EXTRACTION`, `PERSISTENCE`), and live activity feed.
   - Automatic interval polling fallback if SSE disconnects.

3. **Incremental Evidence Inspection**:
   - Completed rows immediately populate the table and "Recently Completed" list.
   - Users can open the multi-tab `EvidenceDetailModal` to inspect verbatim quotes and discovered sources *while remaining workers are still actively running*.

4. **Non-Destructive Export**:
   - Preserves original dataset column order and values, appending new `Enriched_<attribute>`, `Canonical_Url`, and `Enrichment_Status` columns.

---

## 3. Running Locally

```bash
# Install dependencies
npm install

# Start development server with Turbopack
npm run dev

# Build for production
npm run build
```

The frontend runs on [http://localhost:3000](http://localhost:3000) and communicates with backend services at `:9741` (`research-service`) and `:9743` (`dataset-service`).
