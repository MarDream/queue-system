#!/usr/bin/env bash
# ============================================
# 排队叫号系统 - 本地打包后端镜像并推送到远端 Registry
# ============================================
#
# 用法:
#   ./push-image.sh <registry> [tag]
#
# 示例:
#   ./push-image.sh 43.155.249.87:5000 v1.0.3
#   ./push-image.sh 43.155.249.87:5000
#
# 前置条件:
#   1. 已执行 docker login <registry>
#   2. 本地有 Docker 环境
#   3. 前端由独立 Nginx 部署，需单独构建并发布 dist
#
set -eu

REGISTRY="${1:-}"
if [ -z "$REGISTRY" ]; then
  echo "用法: ./push-image.sh <registry> [tag]"
  echo "示例: ./push-image.sh 43.155.249.87:5000 v1.0.3"
  exit 1
fi

IMAGE_TAG="${2:-}"
if [ -z "$IMAGE_TAG" ]; then
  IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
fi

export IMAGE_TAG

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.standalone.yml"
BACKEND_REMOTE="${REGISTRY}/queue-backend:${IMAGE_TAG}"

echo "============================================"
echo "[queue-system] 目标 Registry : $REGISTRY"
echo "[queue-system] 镜像 tag       : $IMAGE_TAG"
echo "============================================"

echo ""
echo "[1/4] 检查 Registry 登录状态..."
if ! docker login "$REGISTRY" --get-login 2>/dev/null; then
  echo ""
  echo "未登录到 $REGISTRY，请先执行："
  echo "  docker login $REGISTRY"
  echo ""
  exit 1
fi

echo ""
echo "[2/4] 本地构建后端镜像 (IMAGE_TAG=$IMAGE_TAG)..."
docker compose -f "$COMPOSE_FILE" build backend

echo ""
echo "[3/4] 为后端镜像打 tag..."
docker tag "queue-backend:${IMAGE_TAG}" "$BACKEND_REMOTE"

echo ""
echo "[4/4] 推送后端镜像到 $REGISTRY ..."
echo "  -> $BACKEND_REMOTE"
docker push "$BACKEND_REMOTE"

echo ""
echo "============================================"
echo "推送完成"
echo "  后端: $BACKEND_REMOTE"
echo "============================================"
echo ""
echo "在目标服务器上拉取并启动后端："
echo "  docker pull $BACKEND_REMOTE"
echo "  IMAGE_TAG=$IMAGE_TAG docker compose -f docker-compose.standalone.yml up -d backend"
echo ""
echo "前端请单独构建并发布 dist 到独立 Nginx 站点目录。"
