#!/bin/bash
# ============================
# 排队叫号系统 - 离线部署脚本
# 在目标服务器上运行，从 tar 包加载镜像并启动
# ============================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
IMAGE_DIR="$SCRIPT_DIR/images"

echo "=========================================="
echo "  排队叫号系统 - 离线部署工具"
echo "=========================================="
echo ""

# 检查镜像文件是否存在
if [ ! -d "$IMAGE_DIR" ]; then
    echo "错误: 找不到镜像目录 $IMAGE_DIR"
    echo "请确保 images/ 目录存在且包含 tar 镜像文件"
    exit 1
fi

# 只加载后端和前端镜像（MySQL/Redis 已外部部署）
for tar_name in queue-backend.tar queue-frontend.tar; do
    if [ -f "$IMAGE_DIR/$tar_name" ]; then
        echo "[加载] $tar_name ..."
        docker load -i "$IMAGE_DIR/$tar_name"
        echo "  完成"
    fi
done
echo ""

# 检查 .env 文件
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    echo "从模板创建 .env ..."
    cp "$SCRIPT_DIR/.env.example" "$SCRIPT_DIR/.env"
    echo ""
    echo "=========================================="
    echo "  重要: 请编辑 .env 文件修改以下配置"
    echo "=========================================="
    echo "  MYSQL_HOST / MYSQL_PASSWORD  - 外部 MySQL 连接"
    echo "  REDIS_HOST / REDIS_PASSWORD  - 外部 Redis 连接"
    echo "  JWT_SECRET                   - JWT 密钥（必改，至少32字符）"
    echo "  APP_IP                       - 服务器IP或域名（必填）"
    echo ""
    read -p "是否现在编辑 .env？(y/n): " EDIT_ENV
    if [ "$EDIT_ENV" = "y" ] || [ "$EDIT_ENV" = "Y" ]; then
        ${EDITOR:-vi} "$SCRIPT_DIR/.env"
    else
        echo "请稍后手动编辑 $SCRIPT_DIR/.env 后重新运行此脚本"
        exit 0
    fi
fi

echo "=========================================="
echo "  启动服务"
echo "=========================================="
echo ""

cd "$SCRIPT_DIR"

# 使用 standalone 模式（仅 backend + frontend，连接外部 MySQL/Redis）
docker compose -f docker-compose.standalone.yml up -d

echo ""
echo "等待服务启动..."
sleep 5

# 检查状态
echo ""
echo "=========================================="
echo "  服务状态"
echo "=========================================="
echo ""
docker compose -f docker-compose.standalone.yml ps

echo ""
echo "=========================================="
echo "  部署完成！"
echo "=========================================="
echo ""
source "$SCRIPT_DIR/.env" 2>/dev/null || true
echo "  前端访问: http://${APP_IP}:${FRONTEND_PORT:-80}"
echo "  管理后台: http://${APP_IP}:${FRONTEND_PORT:-80}/#/admin"
echo ""
echo "  查看日志: docker compose -f docker-compose.standalone.yml logs -f"
echo "  停止服务: docker compose -f docker-compose.standalone.yml down"
echo "  重启服务: docker compose -f docker-compose.standalone.yml restart"
echo ""
