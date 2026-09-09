# Data Enrichment Frontend

Next.js 15 client application providing a modern, interactive web interface for dataset ingestion, schema profiling, user-directed enrichment, real-time concurrent execution observability, and multi-tab evidence verification.

**Port**: `3000`  
**Framework**: Next.js 15 (App Router, Turbopack, Tailwind CSS)

---

### Authoritative Documentation

All architectural specifications, component breakdowns, and workflows for the frontend have been centralized:

* 📖 **[Frontend Service Guide](../../docs/services/frontend/README.md)**
* 🌊 **[End-to-End Data Flow](../../docs/architecture/data-flow.md)**
* 🔌 **[API Endpoints Catalog](../../docs/api/endpoints.md)**
* 🏛️ **[System Architecture](../../docs/architecture/overview.md)**

---

### Quick Start

```bash
# Install dependencies
npm install

# Start development server with Turbopack
npm run dev

# Build for production
npm run build
```

The frontend runs on [http://localhost:3000](http://localhost:3000) and communicates with backend services through API Gateway (`:8080`) or directly in development (`:9741`, `:9743`).
