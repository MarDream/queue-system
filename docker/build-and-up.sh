#!/usr/bin/env bash
set -eu

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

IMAGE_TAG="${1:-}"

if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
fi

export IMAGE_TAG

echo "[queue-system] IMAGE_TAG=$IMAGE_TAG"
echo "[queue-system] Working directory: $SCRIPT_DIR"

COMPOSE_FILE="$SCRIPT_DIR/docker-compose.standalone.yml"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "ERROR: Compose file not found: $COMPOSE_FILE"
  exit 1
fi

echo "[queue-system] Compose file: $COMPOSE_FILE"
echo "[queue-system] Starting backend container only"

docker compose -f "$COMPOSE_FILE" up -d --build backend
