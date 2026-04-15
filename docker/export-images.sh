#!/bin/bash
# ============================
# 排队叫号系统 - 镜像导出脚本
# 在构建机器上运行，导出所有镜像为 tar 包
# ============================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/images"
mkdir -p "$OUTPUT_DIR"

# 拉取基础镜像（MySQL + Redis）
echo "=========================================="
echo "  排队叫号系统 - 镜像导出工具"
echo "=========================================="
echo ""

# 拉取第三方镜像
echo "[1/4] 拉取 MySQL 8.0 镜像..."
docker pull mysql:8.0
echo ""

echo "[2/4] 拉取 Redis 7 Alpine 镜像..."
docker pull redis:7-alpine
echo ""

# 构建前后端镜像
echo "[3/4] 构建后端镜像..."
cd "$SCRIPT_DIR"
docker compose build backend
echo ""

echo "[4/4] 构建前端镜像..."
docker compose build frontend
echo ""

# 获取镜像名称
BACKEND_IMAGE=$(docker compose images -q backend 2>/dev/null || echo "")
FRONTEND_IMAGE=$(docker compose images -q frontend 2>/dev/null || echo "")

# 尝试通过 docker images 获取
if [ -z "$BACKEND_IMAGE" ]; then
    BACKEND_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" --filter "reference=*backend*" | head -1)
fi
if [ -z "$FRONTEND_IMAGE" ]; then
    FRONTEND_IMAGE=$(docker images --format "{{.Repository}}:{{.Tag}}" --filter "reference=*frontend*" | head -1)
fi

echo "=========================================="
echo "  导出镜像到 tar 包"
echo "=========================================="
echo ""
echo "  后端镜像: $BACKEND_IMAGE"
echo "  前端镜像: $FRONTEND_IMAGE"
echo ""

# 导出
echo "[1/4] 导出 MySQL 8.0..."
docker save mysql:8.0 -o "$OUTPUT_DIR/mysql-8.0.tar"
echo "  -> mysql-8.0.tar ($(du -sh "$OUTPUT_DIR/mysql-8.0.tar" | cut -f1))"

echo "[2/4] 导出 Redis 7 Alpine..."
docker save redis:7-alpine -o "$OUTPUT_DIR/redis-7-alpine.tar"
echo "  -> redis-7-alpine.tar ($(du -sh "$OUTPUT_DIR/redis-7-alpine.tar" | cut -f1))"

echo "[3/4] 导出后端镜像..."
docker save "$BACKEND_IMAGE" -o "$OUTPUT_DIR/queue-backend.tar"
echo "  -> queue-backend.tar ($(du -sh "$OUTPUT_DIR/queue-backend.tar" | cut -f1))"

echo "[4/4] 导出前端镜像..."
docker save "$FRONTEND_IMAGE" -o "$OUTPUT_DIR/queue-frontend.tar"
echo "  -> queue-frontend.tar ($(du -sh "$OUTPUT_DIR/queue-frontend.tar" | cut -f1))"

echo ""
echo "=========================================="
echo "  导出完成！"
echo "=========================================="
echo ""
echo "  输出目录: $OUTPUT_DIR"
echo "  文件列表:"
ls -lh "$OUTPUT_DIR"/*.tar | awk '{printf "    %-30s %s\n", $NF, $5}'
echo ""
echo "  总大小: $(du -sh "$OUTPUT_DIR" | cut -f1)"
echo ""
echo "  将 docker/ 整个目录复制到目标服务器，然后运行："
echo "    bash load-and-deploy.sh"
echo ""