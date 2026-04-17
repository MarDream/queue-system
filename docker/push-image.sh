#!/usr/bin/env bash
# ============================================
# 排队叫号系统 - 本地打包镜像并推送到远端 Registry
# ============================================
#
# 用法:
#   ./push-image.sh <registry> [tag]
#
# 示例:
#   ./push-image.sh 43.155.249.87:5000 v1.0.3
#   ./push-image.sh 43.155.249.87:5000          # 不传 tag 则自动使用时间戳
#
# 前置条件:
#   1. 已执行 docker login <registry>
#   2. 本地有 Docker 环境
#
set -eu

# ---------- 参数校验 ----------

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

echo "============================================"
echo "[queue-system] 目标 Registry : $REGISTRY"
echo "[queue-system] 镜像 tag       : $IMAGE_TAG"
echo "============================================"

# ---------- 检查登录状态 ----------

echo ""
echo "[1/4] 检查 Registry 登录状态..."
if ! docker login "$REGISTRY" --get-login 2>/dev/null; then
  echo ""
  echo "⚠️  未登录到 $REGISTRY，请先执行："
  echo "   docker login $REGISTRY"
  echo ""
  exit 1
fi

# ---------- 本地构建镜像 ----------

echo ""
echo "[2/4] 本地构建镜像 (IMAGE_TAG=$IMAGE_TAG)..."
docker compose -f docker-compose.standalone.yml build

# ---------- 打 tag ----------

FRONTEND_REMOTE="${REGISTRY}/queue-frontend:${IMAGE_TAG}"
BACKEND_REMOTE="${REGISTRY}/queue-backend:${IMAGE_TAG}"

echo ""
echo "[3/4] 为镜像打 tag..."
docker tag "queue-frontend:${IMAGE_TAG}" "$FRONTEND_REMOTE"
docker tag "queue-backend:${IMAGE_TAG}"  "$BACKEND_REMOTE"

# ---------- 推送 ----------

echo ""
echo "[4/4] 推送镜像到 $REGISTRY ..."
echo "  -> $FRONTEND_REMOTE"
docker push "$FRONTEND_REMOTE"

echo "  -> $BACKEND_REMOTE"
docker push "$BACKEND_REMOTE"

echo ""
echo "============================================"
echo "推送完成"
echo "  前端: $FRONTEND_REMOTE"
echo "  后端: $BACKEND_REMOTE"
echo "============================================"
echo ""
echo "在目标服务器上拉取并启动："
echo "  docker pull $FRONTEND_REMOTE"
echo "  docker pull $BACKEND_REMOTE"
echo ""
echo "  IMAGE_TAG=$IMAGE_TAG docker compose -f docker-compose.standalone.yml up -d"
