#!/usr/bin/env bash
set -eu

IMAGE_TAG="${1:-}"

if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
fi

export IMAGE_TAG

echo "[queue-system] IMAGE_TAG=$IMAGE_TAG"
docker compose -f docker-compose.standalone.yml up -d --build
