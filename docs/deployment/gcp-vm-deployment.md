# GCP VM Production Deployment Guide

This guide details the end-to-end provisioning, hardening, automated CI/CD deployment, and operations of the Data Enrichment AI Intelligence Platform on a Google Cloud Platform (GCP) Compute Engine Virtual Machine using Docker Compose and GitHub Actions.

---

## 1. Production Architecture Overview

The application runs as a 9-container topology within an isolated Docker bridge network (`enrichment-network`). Only the **API Gateway** (`:9738`) and **Frontend UI** (`:3000`) are accessible publicly through the GCP VPC Firewall. All backend microservices (`:9736`, `:9737`, `:9739`, `:9741`, `:9742`, `:9743`) and MySQL (`:3306`) communicate strictly over internal Docker network addresses.

---

## 2. Infrastructure Sizing & Specifications

For stable production operation running all 7 Spring Boot microservices, MySQL, and Next.js:

* **Machine Type**: GCP Compute Engine `e2-standard-4` (4 vCPUs, 16 GB RAM).
* **Boot Disk**: Ubuntu 24.04 LTS, 50 GB SSD (`pd-balanced`).
* **Swap Space**: 4 GB swapfile configured on the host OS (prevents JVM OOM kills during concurrent bursts).
* **Network Tags**: `enrichment-platform`, `http-server`, `https-server`.

---

## 3. Fresh VM Provisioning (Step-by-Step)

### Step 1: Create Compute Engine Instance
Run from the Google Cloud Shell or local authenticated `gcloud` CLI:

```bash
gcloud compute instances create enrichment-platform-vm \
    --project="<YOUR_GCP_PROJECT_ID>" \
    --zone="us-central1-a" \
    --machine-type="e2-standard-4" \
    --network-interface="network-tier=PREMIUM,subnet=default" \
    --scopes="https://www.googleapis.com/auth/cloud-platform" \
    --tags="enrichment-platform,http-server,https-server" \
    --create-disk="auto-delete=yes,boot=yes,image=projects/ubuntu-os-cloud/global/images/family/ubuntu-2404-lts-amd64,mode=rw,size=50,type=pd-balanced"
```

### Step 2: Configure VPC Firewall Ingress Rules
Strictly restrict public traffic to SSH, Frontend, and Gateway:

```bash
# Allow SSH (Port 22)
gcloud compute firewall-rules create allow-enrichment-ssh \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:22 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform

# Allow Frontend UI (Port 3000)
gcloud compute firewall-rules create allow-enrichment-frontend \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:3000 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform

# Allow API Gateway Ingress (Port 9738)
gcloud compute firewall-rules create allow-enrichment-gateway \
    --direction=INGRESS --priority=1000 --network=default --action=ALLOW \
    --rules=tcp:9738 --source-ranges=0.0.0.0/0 --target-tags=enrichment-platform
```

> [!WARNING]
> Do NOT expose ports 3306 (MySQL), 9736, 9737, 9739, 9741, 9742, or 9743 to the public internet.

---

### Step 3: Install Docker CE & Configure Host Swap

Connect to the VM via SSH:

```bash
# 1. Update package lists and install certificates
sudo apt-get update && sudo apt-get install -y ca-certificates curl gnupg

# 2. Add Docker's official GPG key and APT repository
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 3. Add VM user to docker group
sudo usermod -aG docker $USER
newgrp docker

# 4. Provision 4GB Swap Space
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

### Step 4: Clone Repository & Configure `.env`

```bash
git clone https://github.com/parvagr247/data-enrichment-ai-intelligence.git ~/data-enrichment-ai-intelligence
cd ~/data-enrichment-ai-intelligence/infrastructure/docker

cp .env.example .env
nano .env
```

Set the production variables:
```bash
VM_IP=$(curl -s ifconfig.me)

NEXT_PUBLIC_GATEWAY_URL=http://${VM_IP}:9738
GATEWAY_CORS_ALLOWED_ORIGINS=http://${VM_IP}:3000,http://localhost:3000

MYSQL_ROOT_PASSWORD=generate_strong_root_password
MYSQL_PASSWORD=generate_strong_app_password
JWT_SECRET=$(openssl rand -base64 32)

GEMINI_API_KEY=your_production_gemini_key
SEARCH_PROVIDER_NAME=tavily
SEARCH_PROVIDER_API_KEY=your_production_tavily_key
```

---

### Step 5: Initial Production Launch

```bash
cd ~/data-enrichment-ai-intelligence/infrastructure/docker
chmod +x deploy.sh
./deploy.sh
```

---

## 4. Automated CI/CD with GitHub Actions

The repository includes `.github/workflows/ci.yml` to automatically build container images, push them to Google Artifact Registry, and execute an automated zero-downtime rolling update via SSH.

### GitHub Repository Secrets & Variables
Configure these in **Settings &rarr; Secrets and variables &rarr; Actions**:

#### Secrets
* `GCP_SA_KEY`: Service account JSON key with `roles/artifactregistry.writer`.
* `GCP_SSH_PRIVATE_KEY`: Private SSH key matching the public key in `~/.ssh/authorized_keys` on the VM.

#### Variables
* `GCP_PROJECT_ID`: GCP Project ID.
* `GCP_REGION`: Artifact Registry region (e.g., `us-central1`).
* `GCP_ARTIFACT_REPOSITORY`: Repository name in Artifact Registry.
* `GCP_VM_HOST`: External IP address of your GCP VM.
* `GCP_VM_USER`: SSH login username (e.g., `ubuntu`).

---

## 5. Rollback Procedure

All container images are tagged with immutable Git commit SHAs:

```bash
# Rollback on VM to previous stable commit
ssh ubuntu@<VM_IP>
cd ~/data-enrichment-ai-intelligence/infrastructure/docker
./deploy.sh <PREVIOUS_STABLE_COMMIT_SHA>
```

The persistent MySQL volume (`mysql_data`) remains intact across redeployments.

---

## 6. Operational Health Checks

```bash
# 1. Container health summary
docker compose -f ~/data-enrichment-ai-intelligence/infrastructure/docker/docker-compose.prod.yml ps

# 2. Public Gateway Actuator Probe
curl -s http://localhost:9738/actuator/health

# 3. Check MySQL Health
docker exec enrichment-mysql mysqladmin ping -h localhost --silent && echo "MySQL is healthy"
```
