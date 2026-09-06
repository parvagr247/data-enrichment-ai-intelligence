# Concept 29: Frontend Docker Hot Reloading, Build Caching, and Cross-OS Volume Isolation

In containerized microservice architectures, developers run the full system—databases, backend services, and frontends—using Docker Compose while editing source code directly on their host machines (often Windows or macOS).

When modern full-stack web frameworks like Next.js (using Turbopack) are containerized, a subtle failure mode frequently emerges: **the host source code is updated with substantial enhancements, but the browser application at `http://localhost:3000` remains stuck on an older version.**

This guide explains the root causes of Docker container staleness, the mechanics of cross-OS file synchronization, and how to architect a reliable, zero-rebuild hot-reload pipeline.

---

## Why This Exists

1. **Decoupled Development vs Daemonized Containers**:
   Developers run `docker compose up -d` to bring up the entire multi-service stack in the background. Without explicit bind mounts, containers run on frozen filesystem layers baked into the Docker image at build time.
2. **Cross-OS Native Incompatibilities**:
   Host machines frequently run Windows or macOS, whereas Docker containers run Linux (e.g., `node:22-alpine`). Directly bind-mounting the entire host directory replaces the Linux-compiled binaries in `node_modules` with incompatible host native modules (such as `@next/swc-win32-x64-msvc` on Windows).
3. **Cache and Lock Collisions**:
   Next.js generates `.next` cache and build artifacts. If the host and container share the same `.next` folder, lock contention and platform-specific metadata corrupt Turbopack compilation.

---

## The Problem: Why `localhost:3000` Served Stale Code

During development of the Data Enrichment AI Intelligence Platform, the frontend source in `apps/frontend/` was upgraded with major features:
* Dataset ingestion and file profiling
* Bounded parallel concurrency status indicators
* Multi-tab evidence corroboration modals
* Job cancellation controls

However, visiting `http://localhost:3000` continued to show the initial single-entity prototype.

### Root Cause 1: Docker Compose Watch vs Daemonized Compose
The Compose file configured `develop.watch`:

```yaml
# Incomplete configuration in docker-compose-dev-all.yml
frontend:
  build:
    context: ../../apps/frontend
    dockerfile: Dockerfile
  # Missing 'volumes:' mount!
  develop:
    watch:
      - path: ../../apps/frontend
        target: /app
        action: sync
```

Docker Compose Watch is **opt-in** and only runs when invoked with:
```bash
docker compose up --watch
# or
docker compose watch
```

When developers run the standard daemon command (`docker compose up -d`), Docker Compose **completely ignores** `develop.watch`. Because `volumes:` was missing, the container ran solely on the immutable image layer.

### Root Cause 2: Stale Docker Image Re-use
By design, `docker compose up -d` checks if the image `data-enrich-intell-project-frontend:latest` exists locally. If the image exists, Docker **does not rebuild** it even if host files have changed. Unless `--build` is passed, the container boots using days-old image layers.

### Root Cause 3: Asymmetry Between Backend and Frontend
In `docker-compose-dev-all.yml`, all backend services had explicit bind mounts:
```yaml
# Backend services had volumes mounted
volumes:
  - maven_cache:/root/.m2
  - ../../apps/backend/dataset-service/pom.xml:/app/pom.xml
  - ../../apps/backend/dataset-service/src:/app/src
```
Backend services immediately reflected host Java changes, creating the confusing illusion that the entire Compose stack was auto-updating when only the backend was actually mounted.

---

## The Solution: Anonymous Volume Shadowing & Polling

To provide seamless development where editing files on the host instantly triggers hot-reloads inside Docker without OS collisions, we use **Anonymous Volume Shadowing**.

```
Host (Windows / macOS)                           Docker Container (Linux Alpine)
┌──────────────────────────────────────┐        ┌────────────────────────────────────┐
│ apps/frontend/                       │        │ /app                               │
│  ├── app/ ────────── (Bind Mount) ──────────> │  ├── app/                          │
│  ├── components/ ─── (Bind Mount) ──────────> │  ├── components/                   │
│  ├── lib/ ────────── (Bind Mount) ──────────> │  ├── lib/                          │
│  ├── services/ ───── (Bind Mount) ──────────> │  ├── services/                     │
│  ├── types/ ──────── (Bind Mount) ──────────> │  ├── types/                        │
│  ├── package.json ── (Bind Mount) ──────────> │  ├── package.json                  │
│                                      │        │                                    │
│  ├── node_modules/   (Host Win x64)  │   X    │  ├── node_modules/ (Linux Alpine)  │
│  │   (SHADOWED & ISOLATED)           │        │   └── [Anonymous Docker Volume]    │
│                                      │        │                                    │
│  ├── .next/          (Host Win cache)│   X    │  ├── .next/        (Linux Turbopack│
│  │   (SHADOWED & ISOLATED)           │        │   └── [Anonymous Docker Volume]    │
└──────────────────────────────────────┘        └────────────────────────────────────┘
```

### 1. Compose Volume Configuration

Update `docker-compose-dev-all.yml` to specify both the root bind mount and anonymous volumes:

```yaml
frontend:
  build:
    context: ../../apps/frontend
    dockerfile: Dockerfile
  container_name: enrichment-frontend
  restart: unless-stopped
  ports:
    - "3000:3000"
  environment:
    NODE_ENV: development
    PORT: 3000
    HOSTNAME: "0.0.0.0"
    WATCHPACK_POLLING: "true" # Enables inotify polling across Docker host virtualization
    NEXT_PUBLIC_RESEARCH_SERVICE_URL: ${NEXT_PUBLIC_RESEARCH_SERVICE_URL:-http://localhost:9741}
    NEXT_PUBLIC_AI_INTELLIGENT_SERVICE_URL: ${NEXT_PUBLIC_AI_INTELLIGENT_SERVICE_URL:-http://localhost:9742}
    NEXT_PUBLIC_DATASET_SERVICE_URL: ${NEXT_PUBLIC_DATASET_SERVICE_URL:-http://localhost:9743}
  volumes:
    - ../../apps/frontend:/app         # Live source code bind mount
    - /app/node_modules                # Anonymous volume: preserves container's Linux node_modules
    - /app/.next                       # Anonymous volume: isolates container's Turbopack cache
  develop:
    watch:
      - path: ../../apps/frontend/package.json
        action: rebuild
      - path: ../../apps/frontend/package-lock.json
        action: rebuild
      - path: ../../apps/frontend/Dockerfile
        action: rebuild
      - path: ../../apps/frontend/next.config.ts
        action: rebuild
      - path: ../../apps/frontend
        target: /app
        action: sync
        ignore:
          - node_modules/
          - .next/
```

### How Anonymous Volume Shadowing Works
1. When the image is built (`docker compose build`), `npm install` runs in `/app`, creating `/app/node_modules` with Linux-compatible binaries.
2. At runtime, Docker evaluates volume mounts in order of specificity:
   - It bind-mounts `../../apps/frontend` onto `/app`.
   - It then mounts the anonymous volumes directly over `/app/node_modules` and `/app/.next`.
3. Because the sub-mounts are more specific than `/app`, Docker populates `/app/node_modules` from the image's pre-installed Linux files, completely hiding the host's Windows `node_modules`!
4. Any change made to `.tsx`, `.ts`, or `.css` files on the host is immediately reflected inside `/app` in the container.

### 2. Filesystem Polling via `WATCHPACK_POLLING=true`
On Windows and macOS, file system events (`inotify`) often fail to propagate across the virtualization hypervisor (WSL2 / Hyper-V / VirtioFS).
Next.js Turbopack natively supports `WATCHPACK_POLLING="true"`, which instructs the file watcher to poll for changes at regular intervals, ensuring 100% reliable change detection regardless of host OS.

### 3. Strict `.dockerignore` Hygiene
Ensure the Docker build context does not copy host build artifacts:

```
# apps/frontend/.dockerignore
node_modules
.next
*.tsbuildinfo
.env*.local
npm-debug.log*
yarn-debug.log*
yarn-error.log*
.git
.idea
.DS_Store
coverage
build
```

---

## Verification & Diagnostic Checklist

When debugging Docker container freshness issues, run these diagnostic steps:

| Step | Command | What to Verify |
| :--- | :--- | :--- |
| **1. Check Image Age** | `docker inspect <image> \| grep Created` | Ensure the image timestamp matches your recent code modifications. |
| **2. Inspect Mounts** | `docker inspect <container> \| grep -A 5 Mounts` | Verify the source path points to your active workspace and sub-volumes are mounted. |
| **3. Test Container Files** | `docker exec <container> head -n 30 /app/app/page.tsx` | Confirm the container sees the latest source code. |
| **4. Check Turbopack Logs** | `docker logs --tail 30 <container>` | Look for `GET / 200` compilation logs and zero compiler errors. |
| **5. Test Live Hot-Reload** | Add a temporary comment in host `.tsx` and run `docker exec <container> grep "..." /app/...` | Confirm the edit appears inside the container instantly without rebuilding. |

---

## Summary
By combining **host bind mounts** with **anonymous volume shadowing** and **filesystem polling**, developers achieve instant hot-reloading during `docker compose up -d` while preserving cross-OS safety and dependency isolation.
