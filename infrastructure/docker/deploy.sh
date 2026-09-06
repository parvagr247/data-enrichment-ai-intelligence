# It contains Server Side Orchestration Deployment
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

echo "======================================================================"
echo " Starting Data Enrichment AI Intelligence Platform Deployment"
echo " Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "======================================================================"

# 1. Load local .env if available
if [ -f .env ]; then
  echo "[INFO] Loading configuration from ${SCRIPT_DIR}/.env"
  # Export existing variables without overriding explicitly set environment variables
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
else
  echo "[WARN] No .env file found at ${SCRIPT_DIR}/.env; using environment variables."
fi

# 2. Extract and validate IMAGE_TAG and IMAGE_PREFIX
DEPLOY_TAG="${1:-${IMAGE_TAG:-}}"
export IMAGE_TAG="${DEPLOY_TAG}"
export IMAGE_PREFIX="${IMAGE_PREFIX:-}"

if [ -z "${IMAGE_TAG}" ]; then
  echo "[ERROR] IMAGE_TAG is required. Provide it as an argument or set IMAGE_TAG environment variable."
  echo "        Example: ./deploy.sh 4a9f3b2"
  exit 1
fi

echo "[INFO] Target Container Image Tag: ${IMAGE_TAG}"
echo "[INFO] Target Registry Prefix:     ${IMAGE_PREFIX:-'(local default)'}"

# 3. Validate runtime secrets
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ] || [ -z "${MYSQL_PASSWORD:-}" ]; then
  echo "[ERROR] MYSQL_ROOT_PASSWORD and MYSQL_PASSWORD must be configured in environment or .env"
  exit 1
fi

if [ -z "${JWT_SECRET:-}" ]; then
  echo "[ERROR] JWT_SECRET must be configured in environment or .env"
  exit 1
fi

# 4. Check Docker & Docker Compose installation
if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker command not found. Please install Docker."
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] docker compose plugin not found. Please install docker-compose-plugin."
  exit 1
fi

# 5. Configure Artifact Registry authentication if applicable
REGISTRY_HOST=$(echo "${IMAGE_PREFIX}" | cut -d'/' -f1)
if [[ "${REGISTRY_HOST}" == *"docker.pkg.dev"* ]]; then
  echo "[INFO] Configuring Docker authentication for ${REGISTRY_HOST}..."
  if command -v gcloud >/dev/null 2>&1; then
    gcloud auth configure-docker "${REGISTRY_HOST}" --quiet || echo "[WARN] gcloud configure-docker returned non-zero, continuing with existing credentials..."
  fi
fi

# 6. Pull pre-built production container images
echo "----------------------------------------------------------------------"
echo " Pulling container images (${IMAGE_TAG})..."
echo "----------------------------------------------------------------------"
docker compose -f docker-compose.prod.yml pull

# 7. Start / update containers in background
echo "----------------------------------------------------------------------"
echo " Starting production container stack..."
echo "----------------------------------------------------------------------"
docker compose -f docker-compose.prod.yml up -d --remove-orphans

# 8. Healthcheck verification loop
echo "----------------------------------------------------------------------"
echo " Verifying service health status..."
echo "----------------------------------------------------------------------"

MAX_ATTEMPTS=24
SLEEP_INTERVAL=5
ATTEMPT=1
ALL_HEALTHY=false

while [ ${ATTEMPT} -le ${MAX_ATTEMPTS} ]; do
  echo "[INFO] Health check attempt ${ATTEMPT}/${MAX_ATTEMPTS} (elapsed: $(( (ATTEMPT - 1) * SLEEP_INTERVAL ))s)..."

  # Check Gateway Actuator health
  GATEWAY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9738/actuator/health 2>/dev/null || echo "000")
  # Check Frontend HTTP status
  FRONTEND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null || echo "000")

  # Inspect Docker Compose container health states
  UNHEALTHY_COUNT=$(docker compose -f docker-compose.prod.yml ps --format json 2>/dev/null | grep -ic '"unhealthy"' || true)
  EXITED_COUNT=$(docker compose -f docker-compose.prod.yml ps --format json 2>/dev/null | grep -ic '"exited"' || true)

  if [ "${GATEWAY_STATUS}" = "200" ] && [ "${FRONTEND_STATUS}" = "200" ] && [ "${UNHEALTHY_COUNT}" -eq 0 ] && [ "${EXITED_COUNT}" -eq 0 ]; then
    ALL_HEALTHY=true
    break
  fi

  echo "       Gateway (:9738): ${GATEWAY_STATUS} | Frontend (:3000): ${FRONTEND_STATUS} | Unhealthy containers: ${UNHEALTHY_COUNT} | Exited: ${EXITED_COUNT}"
  sleep ${SLEEP_INTERVAL}
  ATTEMPT=$(( ATTEMPT + 1 ))
done

echo "======================================================================"
echo " Container Status Summary"
echo "======================================================================"
docker compose -f docker-compose.prod.yml ps

if [ "${ALL_HEALTHY}" = true ]; then
  echo "======================================================================"
  echo " [SUCCESS] Deployment completed successfully! All services healthy."
  echo " Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo " Tag: ${IMAGE_TAG}"
  echo "======================================================================"
  exit 0
else
  echo "======================================================================"
  echo " [FAILURE] Deployment health verification failed after $(( MAX_ATTEMPTS * SLEEP_INTERVAL ))s."
  echo " Dumping recent logs of failing containers..."
  echo "======================================================================"
  docker compose -f docker-compose.prod.yml logs --tail=50
  exit 1
fi
