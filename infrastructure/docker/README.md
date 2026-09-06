# Docker Infrastructure & Deployment Guide
## Data Enrichment AI Intelligence Platform

This directory contains the canonical Docker Compose topologies, container configurations, and deployment procedures for both local development and production deployment on Google Cloud Platform (GCP) Compute Engine virtual machines.

---

## 1. Quick Navigation

* 🚀 **[Full Step-by-Step GCP VM Production Deployment Guide](./GCP_VM_DEPLOYMENT.md)**: Complete guide covering GCP VM provisioning, VPC firewall rules, UFW, Docker installation, swapfile setup, `.env` configuration, healthchecks, 15 troubleshooting scenarios, and backup/restore.
* 📋 **[Production Environment Template](./.env.example)**: Comprehensive configuration template with full descriptions for all required and optional environment variables.
* 📦 **[Production Docker Compose](./docker-compose.prod.yml)**: Hardened production multi-container configuration with healthchecks, dependency ordering, and network isolation.

---

## 2. Docker Compose Topologies

| Compose File | Target Environment | Exposed Host Ports | Hot Reload | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`docker-compose.prod.yml`** (or `docker-compose.yml`) | **Production / GCP VM** | `3000` (Frontend), `9738` (Gateway) | No | **Hardened 9-container production stack**. All internal services communicate exclusively via internal Docker network. Includes healthchecks and dependency sequencing. |
| **`docker-compose-dev-all.yml`** | **Local Development (All Services)** | `3000`, `3306`, `9741`, `9742`, `9743` | Yes | Local testing stack mounting host source code with volume shadowing for Next.js hot-reloading. |
| **`docker-compose-dev.yml`** | **Local Development (Database Only)** | `3306` (MySQL) | N/A | Starts only MySQL 8.0 for engineers running microservices directly inside an IDE (IntelliJ / VS Code). |

---

## 3. Production Architecture & Network Isolation

In production, external clients (web browsers) only communicate with **Frontend UI (`:3000`)** and **API Gateway (`:9738`)**. All microservices and the database run on an isolated private bridge network (`enrichment-network`) and do not expose ports on the host.

```
                     Internet / Client Browser
                                 │
                                 ▼
                     GCP VM Public IP Address
                                 │
             ┌───────────────────┴───────────────────┐
             ▼ (Port 3000)                           ▼ (Port 9738)
   ┌───────────────────┐                   ┌───────────────────┐
   │    Frontend UI    │                   │    API Gateway    │
   │   (Next.js 15)    │                   │  (Spring Cloud)   │
   └───────────────────┘                   └─────────┬─────────┘
             │                                       │
  (Browser calls Gateway)                            │
             └───────────────────────────────────────┤
                                                     ▼
                  ┌──────────────────────────────────┴──────────────────────────────────┐
                  │ Internal Docker Bridge Network ("enrichment-network")                │
                  │                                                                     │
                  ▼                                  ▼                                  ▼
        ┌───────────────────┐              ┌───────────────────┐              ┌───────────────────┐
        │   Auth Service    │              │ Research Service  │              │  Dataset Service  │
        │      (:9739)      │              │      (:9741)      │              │      (:9743)      │
        └─────────┬─────────┘              └─────────┬─────────┘              └─────────┬─────────┘
                  │                                  │ (Internal HTTP)                  │
                  │                                  ▼                                  │
                  │                        ┌───────────────────┐                        │
                  │                        │  AI Intelligent   │                        │
                  │                        │  Service (:9742)  │                        │
                  │                        └───────────────────┘                        │
                  │                                                                     │
                  └──────────────────────────────────┬──────────────────────────────────┘
                                                     │
                                                     ▼
                                           ┌───────────────────┐
                                           │     MySQL 8.0     │
                                           │  (:3306 Internal) │
                                           └─────────┬─────────┘
                                                     │
                               ┌─────────────────────┴─────────────────────┐
                               ▼                                           ▼
                     ┌───────────────────┐                       ┌───────────────────┐
                     │   Config Server   │                       │ Discovery Server  │
                     │  (:9736 Internal) │                       │  (:9737 Internal) │
                     └───────────────────┘                       └───────────────────┘
```

---

## 4. Production Service & Port Matrix

| Service Container Name | Service Role | Internal Docker Port | Prod Host Port | Dev Host Port | Network Isolation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`enrichment-frontend`** | Next.js 15 Web Dashboard | `3000` | **`3000`** | `3000` | Public Entrypoint |
| **`enrichment-api-gateway`** | Central Gateway & Routing | `9738` | **`9738`** | N/A | Public Entrypoint |
| **`enrichment-auth-service`** | JWT Auth & User Accounts | `9739` | None (`expose`) | N/A | Private Internal |
| **`enrichment-research-service`** | Web Research & Evidence | `9741` | None (`expose`) | `9741` | Private Internal |
| **`enrichment-ai-intelligent-service`** | Gemini AI Extraction | `9742` | None (`expose`) | `9742` | Private Internal |
| **`enrichment-dataset-service`** | Orchestration & Entity Store | `9743` | None (`expose`) | `9743` | Private Internal |
| **`enrichment-mysql`** | Relational Database | `3306` | None (`expose`) | `3306` | Private Internal |
| **`enrichment-config-server`** | Spring Cloud Config Server | `9736` | None (`expose`) | N/A | Private Internal |
| **`enrichment-discovery-server`** | Netflix Eureka Registry | `9737` | None (`expose`) | N/A | Private Internal |

---

## 5. Fast Track: Deploying to a GCP VM

### Prerequisites
* A GCP VM instance (Recommended: `e2-standard-4`, 4 vCPUs, 16 GB RAM, 50 GB SSD, Ubuntu 24.04 LTS).
* GCP VPC Firewall allowing inbound TCP ports: `22` (SSH), `3000` (Frontend), and `9738` (Gateway).

### Step 1: Install Docker & Docker Compose
```bash
# Update package index and install prerequisites
sudo apt-get update && sudo apt-get install -y ca-certificates curl gnupg

# Add Docker's official GPG key and APT repository
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Allow current user to run docker without sudo
sudo usermod -aG docker $USER
newgrp docker
```

### Step 2: Configure Swap Space (Recommended)
```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### Step 3: Clone Repository & Configure Environment
```bash
git clone https://github.com/parvagr247/data-enrichment-ai-intelligence.git
cd data-enrichment-ai-intelligence/infrastructure/docker

# Copy environment template
cp .env.example .env

# Edit .env with your real API keys and IP address
nano .env
```

**Critical `.env` Variables to Set:**
```bash
# Detect VM public IP:
VM_IP=$(curl -s ifconfig.me)

# In .env, update these values:
NEXT_PUBLIC_GATEWAY_URL=http://${VM_IP}:9738
GATEWAY_CORS_ALLOWED_ORIGINS=http://${VM_IP}:3000

# Security Keys
JWT_SECRET=$(openssl rand -base64 48)
MYSQL_PASSWORD=your_strong_mysql_password
MYSQL_ROOT_PASSWORD=your_strong_root_password

# External API Keys (optional for mock mode, required for live AI/search)
GEMINI_API_KEY=your_google_gemini_api_key
SEARCH_PROVIDER_NAME=tavily
SEARCH_PROVIDER_API_KEY=your_tavily_search_api_key
```

### Step 4: Build & Launch Production Stack
```bash
# Launch the stack in detached mode with build
docker compose -f docker-compose.prod.yml up -d --build
```

### Step 5: Verify Deployment
```bash
# Check all 9 containers are running and healthy
docker compose -f docker-compose.prod.yml ps

# Test Gateway health endpoint
curl -f http://localhost:9738/actuator/health

# Open in your web browser:
# http://<YOUR_VM_PUBLIC_IP>:3000
```

---

## 6. Quick Operational Command Reference

### Service Lifecycle
```bash
# Start all containers in background
docker compose -f docker-compose.prod.yml up -d

# Stop all containers (preserves database data volume)
docker compose -f docker-compose.prod.yml down

# Stop and wipe database volume (CAUTION: deletes all data)
docker compose -f docker-compose.prod.yml down -v

# Restart a single service (e.g. dataset-service)
docker compose -f docker-compose.prod.yml restart dataset-service
```

### Log Streaming & Diagnostics
```bash
# Stream logs across all 9 containers
docker compose -f docker-compose.prod.yml logs -f

# View recent logs for a specific service
docker compose -f docker-compose.prod.yml logs -f --tail=100 api-gateway
docker compose -f docker-compose.prod.yml logs -f --tail=100 dataset-service
docker compose -f docker-compose.prod.yml logs -f --tail=100 research-service

# Check resource consumption across containers
docker stats --no-stream
```

### Updating & Redeploying
```bash
# 1. Pull latest code from repository
git pull origin main

# 2. Rebuild and restart containers without downtime to unchanged services
docker compose -f docker-compose.prod.yml up -d --build

# 3. Clean up dangling images to free disk space
docker image prune -f
```

### Database Backup & Restore
```bash
# Backup MySQL database to host disk
docker exec -i enrichment-mysql mysqldump \
  -u root -p"${MYSQL_ROOT_PASSWORD}" \
  enrichment_db > backup_$(date +%Y%m%d_%H%M%S).sql

# Restore MySQL database from dump file
docker exec -i enrichment-mysql mysql \
  -u root -p"${MYSQL_ROOT_PASSWORD}" \
  enrichment_db < backup_file.sql
```

---

## 7. Troubleshooting Quick Reference

| Issue | Quick Fix |
| :--- | :--- |
| **Frontend displays network error or CORS error** | Ensure `NEXT_PUBLIC_GATEWAY_URL=http://<VM_IP>:9738` and `GATEWAY_CORS_ALLOWED_ORIGINS=http://<VM_IP>:3000` are configured in `.env` and rebuild the frontend container: `docker compose -f docker-compose.prod.yml up -d --build frontend`. |
| **Microservice fails to register with Eureka** | Check discovery server status: `curl http://localhost:9737/actuator/health`. Ensure discovery-server is healthy before microservices boot. |
| **Containers killed with Exit 137 (OOM)** | Check available memory with `free -h`. Ensure a 4GB swapfile is created and active. |
| **Database fails to connect on boot** | MySQL takes ~25s to initialize the data directory on first run. Healthcheck conditions ensure other containers wait until `mysqladmin ping` returns healthy. |
| **API Gateway returns 401 Unauthorized** | Register a new user at `http://<VM_IP>:3000/register` or supply the `Authorization: Bearer <token>` header in your requests. |

For the complete 15-scenario troubleshooting guide, see Section 12 in **[GCP_VM_DEPLOYMENT.md](./GCP_VM_DEPLOYMENT.md)**.
