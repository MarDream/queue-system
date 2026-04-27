#!/usr/bin/env bash
set -eu

MODE="${1:-all}"

case "$MODE" in
  all|backend|frontend)
    ;;
  *)
    echo "Usage: ./deploy-remote.sh [all|backend|frontend]"
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.standalone.yml"
ENV_FILE="$SCRIPT_DIR/.env"
FRONTEND_IMAGE="queue-frontend-dist:latest"

compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
    return
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
    return
  fi

  echo "ERROR: Neither 'docker compose' nor 'docker-compose' is available."
  exit 1
}

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: Env file not found: $ENV_FILE"
  echo "Copy docker/.env.remote.example to docker/.env and fill in production values first."
  exit 1
fi

set -a
. "$ENV_FILE"
set +a

deploy_backend() {
  echo "[backend] Rebuilding and restarting backend container..."
  compose_cmd -f "$COMPOSE_FILE" up -d --build backend
}

deploy_frontend() {
  if [ -z "${FRONTEND_DIST_DIR:-}" ]; then
    echo "ERROR: FRONTEND_DIST_DIR is not set in $ENV_FILE"
    exit 1
  fi

  if [ "$FRONTEND_DIST_DIR" = "/" ]; then
    echo "ERROR: FRONTEND_DIST_DIR must not be /"
    exit 1
  fi

  local parent_dir
  local staging_dir

  parent_dir="$(dirname "$FRONTEND_DIST_DIR")"
  staging_dir="$parent_dir/.frontend-dist.staging.$$"

  mkdir -p "$parent_dir"
  rm -rf "$staging_dir"
  mkdir -p "$staging_dir"

  echo "[frontend] Building dist image..."
  docker build -f "$SCRIPT_DIR/frontend/Dockerfile" -t "$FRONTEND_IMAGE" "$REPO_ROOT/queue-system-frontend"

  echo "[frontend] Exporting dist to staging directory..."
  docker run --rm -v "$staging_dir:/output" "$FRONTEND_IMAGE" >/dev/null

  mkdir -p "$FRONTEND_DIST_DIR"
  find "$FRONTEND_DIST_DIR" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  cp -a "$staging_dir"/. "$FRONTEND_DIST_DIR"/
  rm -rf "$staging_dir"

  if [ -n "${NGINX_CONTAINER_NAME:-}" ]; then
    echo "[frontend] Reloading nginx container: $NGINX_CONTAINER_NAME"
    docker exec "$NGINX_CONTAINER_NAME" nginx -s reload >/dev/null 2>&1 || true
  fi
}

case "$MODE" in
  all)
    deploy_backend
    deploy_frontend
    ;;
  backend)
    deploy_backend
    ;;
  frontend)
    deploy_frontend
    ;;
esac

echo ""
echo "Deployment finished."
echo "  Mode          : $MODE"
echo "  Backend port  : ${BACKEND_PORT:-8080}"
if [ "$MODE" != "backend" ]; then
  echo "  Frontend dist : ${FRONTEND_DIST_DIR:-}"
fi
