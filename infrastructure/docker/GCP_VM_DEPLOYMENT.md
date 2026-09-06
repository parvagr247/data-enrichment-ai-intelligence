# Complete GCP VM Production Deployment Guide
## Data Enrichment AI Intelligence Platform

This guide provides the complete, reproducible, copy-pasteable deployment procedure for running the Data Enrichment AI Intelligence Platform on a fresh Google Cloud Platform (GCP) Compute Engine Virtual Machine using Docker Compose.

---

## 1. Target Production Architecture

The platform runs as a distributed multi-service architecture inside an isolated Docker bridge network (`enrichment-network`). Only the **API Gateway (`:9738`)** and **Frontend UI (`:3000`)** are exposed to external traffic. All internal microservices communicate using internal Docker DNS names (`auth-service`, `research-service`, `ai-intelligent-service`, `dataset-service`, `mysql`, `config-server`, `discovery-server`).

```
                              Internet / External Client Browser
                                              ¦
                                              ?
                                    GCP VM External IP
                                              ¦
                         +-----------------------------------------+
                         ¦                                         ¦
                         ? (Port 3000)                             ? (Port 9738)
               +-------------------+                     +-------------------+
               ¦    Frontend UI    ¦                     ¦    API Gateway    ¦
               ¦   (Next.js 15)    ¦                     ¦  (Spring Cloud)   ¦
               +-------------------+                     +-------------------+
                                                                   ¦
                                 +---------------------------------+---------------------------------+
                                 ¦ (Internal Network)              ¦ (Internal Network)              ¦ (Internal Network)
                                 ?                                 ?                                 ?
                       +-------------------+             +-------------------+             +-------------------+
                       ¦   Auth Service    ¦             ¦ Research Service  ¦             ¦  Dataset Service  ¦
                       ¦     (:9739)       ¦             ¦     (:9741)       ¦             ¦     (:9743)       ¦
                       +-------------------+             +-------------------+             +-------------------+
                                 ¦                                 ¦ (Internal)                      ¦
                                 ¦                                 ?                                 ¦
                                 ¦                       +-------------------+                       ¦
                                 ¦                       ¦   AI Intelligent  ¦                       ¦
                                 ¦                       ¦  Service (:9742)  ¦                       ¦
                                 ¦                       +-------------------+                       ¦
                                 ¦                                                                   ¦
                                 +-------------------------------------------------------------------+
                                                                 ?
                                                       +-------------------+
                                                       ¦   MySQL 8.0 DB    ¦
                                                       ¦  (:3306 Internal) ¦
                                                       +-------------------+
                                                                 ?
                                          +---------------------------------------------+
                                          ¦                                             ¦
                                +-------------------+                         +-------------------+
                                ¦   Config Server   ¦                         ¦  Discovery Server ¦
                                ¦  (:9736 Internal) ¦                         ¦  (:9737 Internal) ¦
                                +-------------------+                         +-------------------+
```

### Service Port Map

| Component | Port | Exposure | Role | Health Endpoint |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway** | `9738` | **Public** | Primary reverse proxy, JWT & API Key validation, routing | `http://localhost:9738/actuator/health` |
| **Frontend UI** | `3000` | **Public** | Next.js 15 client dashboard & SSE live execution | `http://localhost:3000` |
| **Auth Service** | `9739` | **Internal** | User registration, login, JWT token issuance | `http://localhost:9739/actuator/health` |
| **Research Service** | `9741` | **Internal** | Multi-query discovery, scraping, snippet fallback | `http://localhost:9741/actuator/health` |
| **AI Intelligent Service**| `9742` | **Internal** | Gemini 2.0 Flash extraction & multi-dimensional scoring | `http://localhost:9742/actuator/health` |
| **Dataset Service** | `9743` | **Internal** | Ingestion profiling, concurrency executor, persistence | `http://localhost:9743/actuator/health` |
| **MySQL Database** | `3306` | **Internal** | Relational data (`entities`, `users`, Flyway migrations) | `mysqladmin ping` |
| **Config Server** | `9736` | **Internal** | Centralized Spring Cloud Config repository | `http://localhost:9736/actuator/health` |
| **Discovery Server** | `9737` | **Internal** | Netflix Eureka service discovery & registration | `http://localhost:9737/actuator/health` |

---

## 2. Step 1: Provision GCP Compute Engine Virtual Machine

### Recommended Machine Specifications
* **Machine Type**: `e2-standard-4` (4 vCPU, 16 GB RAM) recommended for building all Java containers concurrently.
  *(Minimum acceptable for runtime after building: `e2-standard-2` with 2 vCPU, 8 GB RAM and 4 GB swap).*
* **Operating System**: **Ubuntu 24.04 LTS (x86/64, amd64)**
* **Boot Disk**: **50 GB Balanced Persistent Disk** (SSD/balanced recommended for Maven dependency cache and Docker build layers).
* **Region/Zone**: Choose the region closest to your location (e.g., `us-central1-a`, `asia-south1-a`, `europe-west1-b`).

### Option A: Provision via Google Cloud CLI (`gcloud`)
Run from your local terminal or Google Cloud Shell:

```bash
gcloud compute instances create enrichment-platform-vm \
    --project="<YOUR_GCP_PROJECT_ID>" \
    --zone="us-central1-a" \
    --machine-type="e2-standard-4" \
    --network-interface="network-tier=PREMIUM,subnet=default" \
    --maintenance-policy="MIGRATE" \
    --scopes="https://www.googleapis.com/auth/cloud-platform" \
    --tags="enrichment-platform,http-server,https-server" \
    --create-disk="auto-delete=yes,boot=yes,image=projects/ubuntu-os-cloud/global/images/family/ubuntu-2404-lts-amd64,mode=rw,size=50,type=pd-balanced"
```

### Option B: Provision via Google Cloud Console
1. Open [Google Cloud Console > Compute Engine > VM Instances](https://console.cloud.google.com/compute/instances).
2. Click **Create Instance**.
3. Name: `enrichment-platform-vm`.
4. Region: Choose your preferred region.
5. Machine Configuration: General-purpose > **E2** > Preset > **e2-standard-4** (4 vCPU, 16 GB memory).
6. Boot Disk: Click **Change** > OS: **Ubuntu** > Version: **Ubuntu 24.04 LTS** > Size: **50 GB** > **Select**.
7. Firewall: Check **Allow HTTP traffic** and **Allow HTTPS traffic**.
8. Networking > Network tags: Add `enrichment-platform`.
9. Click **Create**.

---

## 3. Step 2: Configure GCP VPC Firewall Rules

In GCP, network traffic is blocked by default. You must create firewall rules to allow external access to the public entry points (**Port 22 for SSH**, **Port 3000 for Frontend**, **Port 9738 for API Gateway**).

### Via `gcloud` CLI:
```bash
# 1. Allow SSH (Port 22)
gcloud compute firewall-rules create allow-enrichment-ssh \
    --direction=INGRESS \
    --priority=1000 \
    --network=default \
    --action=ALLOW \
    --rules=tcp:22 \
    --source-ranges=0.0.0.0/0 \
    --target-tags=enrichment-platform

# 2. Allow Frontend UI (Port 3000)
gcloud compute firewall-rules create allow-enrichment-frontend \
    --direction=INGRESS \
    --priority=1000 \
    --network=default \
    --action=ALLOW \
    --rules=tcp:3000 \
    --source-ranges=0.0.0.0/0 \
    --target-tags=enrichment-platform

# 3. Allow API Gateway (Port 9738)
gcloud compute firewall-rules create allow-enrichment-gateway \
    --direction=INGRESS \
    --priority=1000 \
    --network=default \
    --action=ALLOW \
    --rules=tcp:9738 \
    --source-ranges=0.0.0.0/0 \
    --target-tags=enrichment-platform
```

> [!CAUTION]
> **DO NOT** create firewall rules for ports `3306`, `9736`, `9737`, `9739`, `9741`, `9742`, or `9743`. Those services must remain internal to the VM.

---

## 4. Step 3: Connect via SSH & Configure VM Firewall (UFW)

Connect to your VM via SSH:
```bash
gcloud compute ssh enrichment-platform-vm --zone="us-central1-a"
```
*(Or use the SSH button in the GCP Web Console).*

### Configure Host Firewall (UFW)
Always enable SSH **before** turning on UFW to avoid locking yourself out:

```bash
# 1. Ensure UFW allows SSH before enabling
sudo ufw allow 22/tcp comment 'SSH access'

# 2. Allow public application ports
sudo ufw allow 3000/tcp comment 'Next.js Frontend UI'
sudo ufw allow 9738/tcp comment 'Spring Cloud API Gateway'

# 3. Enable UFW
sudo ufw --force enable

# 4. Verify firewall status
sudo ufw status verbose
```

---

## 5. Step 4: Install Docker & Docker Compose Plugin

Run the official Docker installation script on the Ubuntu VM:

```bash
# 1. Update system package index
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release git

# 2. Add Docker official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 3. Set up the Docker apt repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. Install Docker Engine, containerd, and Docker Compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 5. Add current user to the docker group (allows running docker without sudo)
sudo usermod -aG docker $USER

# 6. Apply group changes to current shell
newgrp docker

# 7. Verify installations
docker --version
docker compose version
```

Expected output:
* `Docker version 27.x.x` or higher
* `Docker Compose version v2.x.x` or higher

---

## 6. Step 5: Clone Repository & Setup Swap Space

### Recommended: Add 4 GB Swap Space
Compiling multiple Spring Boot and Next.js applications requires sufficient memory. On smaller VMs, adding a swapfile prevents Out-Of-Memory (OOM) compiler crashes:

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### Clone the Repository
```bash
# Clone the repository
git clone <YOUR_REPOSITORY_URL> data-enrichment-ai-intelligence

# Enter the project directory
cd data-enrichment-ai-intelligence

# Checkout your production release branch (e.g., main)
git checkout main
```

---

## 7. Step 6: Configure Production Secrets & Environment Variables

Create the production `.env` file from the provided template in `infrastructure/docker/`:

```bash
cp infrastructure/docker/.env.example infrastructure/docker/.env
```

Now generate a cryptographically secure 256-bit JWT secret:
```bash
openssl rand -base64 32
```

Edit `infrastructure/docker/.env`:
```bash
nano infrastructure/docker/.env
```

Find your VM's public IP by running:
```bash
curl -s ifconfig.me
```

Configure the variables as follows:

```ini
# ==============================================================================
# Production Environment Configuration (.env)
# ==============================================================================
APP_ENV=production
COMPOSE_PROJECT_NAME=data-enrichment-production

# --- Database ---
MYSQL_ROOT_PASSWORD=SuperStrongRootSecret992!
MYSQL_DATABASE=enrichment_db
MYSQL_USER=enrichment_user
MYSQL_PASSWORD=SuperStrongUserPassword883!

# --- Security & Auth ---
# Paste your generated 256-bit key from `openssl rand -base64 32`:
JWT_SECRET=q7vJm2X9kP4sL8wR3nY6tB1cF5hQ0zAa9UeD7xK2pNs=
JWT_EXPIRATION_MS=86400000

# Optional API key for X-API-Key header (leave blank for open access):
GATEWAY_API_KEY=

# Set to your VM's public IP so the browser can make CORS requests:
GATEWAY_CORS_ALLOWED_ORIGINS=http://<YOUR_VM_PUBLIC_IP>:3000,http://localhost:3000

# --- AI Intelligence Service (:9742) ---
GEMINI_API_KEY=<YOUR_GOOGLE_GEMINI_API_KEY>
AI_MODEL=gemini-2.0-flash
AI_MOCK_MODE=false

# --- Research Service (:9741) ---
SEARCH_PROVIDER_NAME=tavily
SEARCH_PROVIDER_API_KEY=<YOUR_TAVILY_API_KEY>
SEARCH_PROVIDER_BASE_URL=https://api.tavily.com
SEARCH_DISCOVERY_MAX_RESULTS=5
SEARCH_DISCOVERY_TIMEOUT_MS=4000
WEB_FETCH_CONNECT_TIMEOUT_MS=3000
WEB_FETCH_READ_TIMEOUT_MS=5000
WEB_FETCH_MAX_RESPONSE_SIZE_MB=5
RESEARCH_MAX_SOURCES=5
RESEARCH_MAX_CONTENT_LENGTH=50000

# --- Dataset Service (:9743) ---
ENRICHMENT_CONCURRENCY=3
ENRICHMENT_QUEUE_CAPACITY=500
ENRICHMENT_ENTITY_TIMEOUT=60
ENRICHMENT_JOB_TIMEOUT=30
ENRICHMENT_AI_ENABLED=true

# --- Frontend (:3000) ---
# CRITICAL: Must point to the public Gateway address accessible by the browser
NEXT_PUBLIC_GATEWAY_URL=http://<YOUR_VM_PUBLIC_IP>:9738
NEXT_PUBLIC_GATEWAY_API_KEY=
```

Restrict file permissions so only your user can read the secrets:
```bash
chmod 600 infrastructure/docker/.env
```

---

## 8. Step 7: Build & Launch Production Stack

### Validate Configuration Syntax First
```bash
docker compose \
  -f infrastructure/docker/docker-compose.prod.yml \
  --env-file infrastructure/docker/.env \
  config
```

### Build and Start All Services
```bash
docker compose \
  -f infrastructure/docker/docker-compose.prod.yml \
  --env-file infrastructure/docker/.env \
  up -d --build
```

### Monitor Startup Progress
The services start in strict, health-verified dependency sequence:
1. `mysql` initializes database and starts healthcheck.
2. `config-server` starts and loads `/config`.
3. `discovery-server` starts Eureka registry after Config Server is healthy.
4. `auth-service`, `dataset-service`, `ai-intelligent-service`, and `research-service` initialize and register with Eureka after MySQL and Discovery Server are healthy.
5. `api-gateway` starts routing after Discovery and Auth services are healthy.
6. `frontend` starts after API Gateway is healthy.

```bash
# Watch container statuses
watch -n 2 docker compose -f infrastructure/docker/docker-compose.prod.yml ps
```

Wait until all containers show status **`Up (healthy)`**.

---

## 9. Step 8: Health Verification & Sanity Checks

Run these commands on the VM to verify every layer:

```bash
# 1. Verify MySQL
docker exec -it enrichment-mysql mysqladmin ping -u root -pSuperStrongRootSecret992!

# 2. Verify Config Server
curl -s http://localhost:9736/actuator/health | grep '"status":"UP"'

# 3. Verify Eureka Discovery Server
curl -s http://localhost:9737/actuator/health | grep '"status":"UP"'

# 4. Verify Auth Service (Internal via docker network)
docker exec -it enrichment-api-gateway curl -s http://auth-service:9739/actuator/health

# 5. Verify Research Service
docker exec -it enrichment-api-gateway curl -s http://research-service:9741/actuator/health

# 6. Verify AI Intelligent Service
docker exec -it enrichment-api-gateway curl -s http://ai-intelligent-service:9742/actuator/health

# 7. Verify Dataset Service
docker exec -it enrichment-api-gateway curl -s http://dataset-service:9743/actuator/health

# 8. Verify Public Gateway Health (Public Port 9738)
curl -s http://localhost:9738/actuator/health

# 9. Verify Frontend (Public Port 3000)
curl -I http://localhost:3000
```

---

## 10. Step 9: User Workflow & Verification Walkthrough

Once healthy, open your web browser on your computer:

```
http://<YOUR_VM_PUBLIC_IP>:3000
```

### Complete End-to-End Workflow Verification:

1. **User Registration**:
   Test creating an account via the API Gateway:
   ```bash
   curl -X POST http://<YOUR_VM_PUBLIC_IP>:9738/api/v1/auth/register \
     -H "Content-Type: application/json" \
     -d '{"name":"Admin User","email":"admin@example.com","password":"Password123!"}'
   ```
   *Expected: HTTP 201 Created with JWT token and user profile.*

2. **User Login**:
   ```bash
   curl -X POST http://<YOUR_VM_PUBLIC_IP>:9738/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"admin@example.com","password":"Password123!"}'
   ```
   *Expected: HTTP 200 OK with Bearer token.*

3. **Frontend Dashboard Ingestion**:
   - Open `http://<YOUR_VM_PUBLIC_IP>:3000` in your browser.
   - Drag and drop `data/samples/v1-regression-dataset.csv` into the Upload dropzone.
   - Observe automatic column role profiling (`Name`, `Company`, `Role`, `LinkedIn`).
   - Configure research depth (`NORMAL`) and enter your target objective prompt:
     *"Identify and enrich the profiles of people most likely to help with a Java/Spring Boot backend internship."*
   - Click **Run Enrichment Job**.
   - Watch real-time SSE progress indicators transition smoothly across stages:
     `DISCOVERING` $\rightarrow$ `COLLECTING_SOURCES` $\rightarrow$ `EXTRACTING_EVIDENCE` $\rightarrow$ `AI_ENRICHMENT` $\rightarrow$ `ASSESSING` $\rightarrow$ `PERSISTING`.
   - Inspect the resulting enriched table, multi-dimensional scores (0–100), talking points, and source provenance pills (`FULL_PAGE` vs `SEARCH_SNIPPET`).

---

## 11. Step 10: Production Operations & Maintenance

### Viewing Service Logs
```bash
# Follow logs across all containers
docker compose -f infrastructure/docker/docker-compose.prod.yml --env-file infrastructure/docker/.env logs -f

# Follow logs for a specific service
docker compose -f infrastructure/docker/docker-compose.prod.yml logs -f api-gateway
docker compose -f infrastructure/docker/docker-compose.prod.yml logs -f research-service
docker compose -f infrastructure/docker/docker-compose.prod.yml logs -f dataset-service
docker compose -f infrastructure/docker/docker-compose.prod.yml logs -f auth-service

# View the last 200 lines of logs
docker compose -f infrastructure/docker/docker-compose.prod.yml logs --tail=200 api-gateway
```

### Update & Zero-Data-Loss Redeployment
When you push code updates to your Git repository:

```bash
cd ~/data-enrichment-ai-intelligence

# 1. Pull latest changes
git pull origin main

# 2. Rebuild changed containers
docker compose -f infrastructure/docker/docker-compose.prod.yml --env-file infrastructure/docker/.env build

# 3. Re-launch stack (Docker updates only containers with changes; database volume persists intact)
docker compose -f infrastructure/docker/docker-compose.prod.yml --env-file infrastructure/docker/.env up -d

# 4. Verify health
docker compose -f infrastructure/docker/docker-compose.prod.yml ps
```

### Database Backup & Restore Procedure

#### Create Backup:
```bash
# Create timestamped SQL dump
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
docker exec enrichment-mysql mysqldump -u root -pSuperStrongRootSecret992! enrichment_db > ~/backup_enrichment_${TIMESTAMP}.sql

# Verify dump file size
ls -lh ~/backup_enrichment_${TIMESTAMP}.sql
```

#### Restore from Backup:
```bash
docker exec -i enrichment-mysql mysql -u root -pSuperStrongRootSecret992! enrichment_db < ~/backup_enrichment_YYYYMMDD_HHMMSS.sql
```

### Stopping & Restarting Services
```bash
# Restart a single service (e.g., after changing an environment variable)
docker compose -f infrastructure/docker/docker-compose.prod.yml restart research-service

# Stop all services safely (preserves database data volume)
docker compose -f infrastructure/docker/docker-compose.prod.yml down

# Start services back up
docker compose -f infrastructure/docker/docker-compose.prod.yml --env-file infrastructure/docker/.env up -d
```

---

## 12. Production Troubleshooting Guide

| # | Symptom | Diagnostic Command | Root Cause & Resolution |
|---|---|---|---|
| 1 | **Config Server Unavailable** | `docker compose ... logs config-server` | Check that `./config` volume is mounted read-only and `SPRING_PROFILES_ACTIVE=native` is set. |
| 2 | **Eureka Unavailable** | `docker compose ... logs discovery-server` | Ensure `config-server` is completely healthy before `discovery-server` boots. Check memory usage (`free -m`). |
| 3 | **Service Not Registered** | `curl http://localhost:9737/eureka/apps` | Eureka discovery registration interval is 10s. Verify service `EUREKA_SERVER_URL=http://discovery-server:9737/eureka/`. |
| 4 | **Gateway Cannot Discover Service** | `curl http://localhost:9738/actuator/health` | Ensure API Gateway routes use Docker DNS names (`http://dataset-service:9743`) rather than `localhost`. |
| 5 | **Database Unavailable** | `docker compose ... logs mysql` | Ensure `mysql_data` volume is writable and `MYSQL_ROOT_PASSWORD` matches application datasource configuration. |
| 6 | **Authentication Fails / 401 Unauthorized** | `docker compose ... logs auth-service` | Verify `JWT_SECRET` in `.env` is identical across `auth-service` and `api-gateway`. Must be at least 256 bits (32 chars). |
| 7 | **JWT Expiration / Signature Error** | `docker compose ... logs api-gateway` | Client token has expired (`JWT_EXPIRATION_MS`) or was signed with a different key. Log in again to obtain a fresh token. |
| 8 | **Frontend Calling Localhost:9738** | Open browser Developer Tools > Network tab | `NEXT_PUBLIC_GATEWAY_URL` in `.env` was set to `localhost`. Change it to `http://<YOUR_VM_PUBLIC_IP>:9738` and rebuild the frontend container (`docker compose ... up -d --build frontend`). |
| 9 | **CORS Errors in Browser** | Browser Console `Access to fetch blocked by CORS` | Update `GATEWAY_CORS_ALLOWED_ORIGINS` in `.env` to include `http://<YOUR_VM_PUBLIC_IP>:3000`, then restart Gateway. |
| 10 | **Port Already in Use** | `sudo ss -tulpn \| grep -E '3000\|9738\|3306'` | Another process on the VM is binding port 3000 or 9738. Stop conflicting service with `sudo systemctl stop <service>` or `sudo kill -9 <PID>`. |
| 11 | **Container Crash Loop (OOM)** | `docker inspect <container> \| grep OOMKilled` | VM ran out of RAM during Maven builds. Add 4 GB swapfile (Step 5) and re-run. |
| 12 | **Environment Variable Missing** | `docker compose ... config` | `.env` file was not found or variable was left blank without a default. Inspect `infrastructure/docker/.env`. |
| 13 | **AI API Key Invalid** | `docker compose ... logs ai-intelligent-service` | Google Gemini API key is missing or expired. Update `GEMINI_API_KEY` in `.env` or set `AI_MOCK_MODE=true`. |
| 14 | **Tavily API Key Invalid** | `docker compose ... logs research-service` | Tavily API key is missing. Set `SEARCH_PROVIDER_NAME=mock` to use rich offline mock data or update `SEARCH_PROVIDER_API_KEY`. |
| 15 | **Database Permission Issue** | `docker compose ... logs dataset-service` | Flyway migration failed due to existing dirty schema. Connect to MySQL and run `DROP DATABASE enrichment_db; CREATE DATABASE enrichment_db;` or repair Flyway table. |

---

## 13. Next Production Hardening Steps (Future Improvements)

When you are ready to transition from raw Public IP access to an enterprise production setup:

1. **Domain & HTTPS Termination (Reverse Proxy)**:
   Place Nginx or Caddy in front of the stack on ports `80` and `443`:
   - Point your DNS A-record to the GCP VM Public IP (`enrichment.yourdomain.com`).
   - Use Certbot / Let's Encrypt for automatic TLS renewal.
   - Configure Nginx to route `/` to `localhost:3000` and `/api/` to `localhost:9738`.
2. **Google Secret Manager**:
   Instead of storing `.env` on disk, inject production secrets at startup using Google Cloud Secret Manager via `gcloud secrets versions access`.
3. **Google Artifact Registry**:
   Set up GitHub Actions CI/CD to build Docker images on code push, tag them, and push them to Google Artifact Registry (`pkg.dev`). On the GCP VM, deployment becomes a simple `docker compose pull && docker compose up -d` without compiling code on the VM.

---

## 14. Production Deployment Checklist

### Before Deployment:
- [ ] GCP VM created with recommended specs (`e2-standard-4`, 50 GB disk, Ubuntu 24.04 LTS).
- [ ] SSH connection established and verified.
- [ ] GCP VPC Firewall allows ports `22`, `3000`, `9738`.
- [ ] UFW host firewall configured and active.
- [ ] Docker Engine and Docker Compose plugin installed.
- [ ] 4 GB swapfile created.
- [ ] Repository cloned to `~/data-enrichment-ai-intelligence`.
- [ ] `infrastructure/docker/.env` created from `.env.example`.
- [ ] Strong database passwords configured in `.env`.
- [ ] 256-bit `JWT_SECRET` generated and configured in `.env`.
- [ ] `NEXT_PUBLIC_GATEWAY_URL` configured to `http://<YOUR_VM_PUBLIC_IP>:9738`.
- [ ] `GATEWAY_CORS_ALLOWED_ORIGINS` configured with frontend IP address.
- [ ] `docker compose config` validation succeeds with zero errors.

### After Deployment:
- [ ] All 9 containers show `Up (healthy)` in `docker compose ps`.
- [ ] Config Server responds with HTTP 200 on `/actuator/health`.
- [ ] Eureka Discovery Server registers all services.
- [ ] MySQL database initialized and Flyway migrations V1 & V2 applied.
- [ ] User registration (`/api/v1/auth/register`) issues a valid JWT token.
- [ ] User login (`/api/v1/auth/login`) succeeds.
- [ ] Frontend UI loads at `http://<YOUR_VM_PUBLIC_IP>:3000`.
- [ ] CSV dataset upload profiles columns successfully.
- [ ] Research and AI enrichment runs and completes with honest status tracking.
- [ ] Enriched entity data persists and survives `docker compose restart`.
