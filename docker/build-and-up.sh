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

# 使用绝对路径指定 compose 文件
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.standalone.yml"

# 检查 compose 文件是否存在
if [ ! -f "$COMPOSE_FILE" ]; then
  echo "ERROR: Compose file not found: $COMPOSE_FILE"
  exit 1
fi

echo "[queue-system] Compose file: $COMPOSE_FILE"

docker compose -f "$COMPOSE_FILE" up -d --build
