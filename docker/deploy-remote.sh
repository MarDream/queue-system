#!/usr/bin/env bash
set -eu

MODE="${1:-all}"

case "$MODE" in
  all|backend|frontend|mysql-init)
    ;;
  *)
    echo "Usage: ./deploy-remote.sh [all|backend|frontend|mysql-init]"
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
VERSION_FILE="$REPO_ROOT/VERSION"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: Env file not found: $ENV_FILE"
  echo "Copy docker/.env.remote.example to docker/.env and fill in production values first."
  exit 1
fi

set -a
. "$ENV_FILE"
set +a

if [ ! -f "$VERSION_FILE" ]; then
  echo "ERROR: Version file not found: $VERSION_FILE"
  exit 1
fi

APP_VERSION="$(tr -d '\r\n' < "$VERSION_FILE")"
if [ -z "$APP_VERSION" ]; then
  echo "ERROR: Version file is empty: $VERSION_FILE"
  exit 1
fi

export APP_VERSION
FRONTEND_IMAGE="queue-frontend-dist:$APP_VERSION"
BACKEND_IMAGE="queue-backend:$APP_VERSION"

# MySQL 字符集初始化函数
init_mysql_charset() {
  echo "[mysql] Checking and setting UTF-8 character set..."

  local mysql_host="${DB_HOST:-mysql8}"
  local mysql_user="${DB_USERNAME:-root}"
  local mysql_pass="${DB_PASSWORD:-}"
  local mysql_db="${DB_NAME:-queue_system}"

  # 检查 MySQL 容器是否运行
  if docker ps --format '{{.Names}}' | grep -q "^${mysql_host}$"; then
    # 设置数据库字符集
    docker exec ${mysql_host} mysql -u${mysql_user} -p${mysql_pass} -e \
      "ALTER DATABASE ${mysql_db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null || true

    # 导入 schema（使用 utf8mb4）
    echo "[mysql] Importing schema with utf8mb4 encoding..."
    docker exec -i ${mysql_host} mysql -u${mysql_user} -p${mysql_pass} --default-character-set=utf8mb4 ${mysql_db} \
      < "${REPO_ROOT}/queue-system-backend/src/main/resources/db/schema.sql" 2>/dev/null || true

    echo "[mysql] UTF-8 character set configured."
  else
    echo "[mysql] MySQL container '${mysql_host}' not found, skipping charset init."
  fi
}

deploy_backend() {
  echo "[backend] Building backend image (version: $APP_VERSION)..."
  docker build \
    --build-arg APP_VERSION="$APP_VERSION" \
    -f "$SCRIPT_DIR/backend/Dockerfile" \
    -t "$BACKEND_IMAGE" \
    "$REPO_ROOT/queue-system-backend"

  echo "[backend] Stopping old container..."
  docker stop queue-backend 2>/dev/null || true
  docker rm queue-backend 2>/dev/null || true

  echo "[backend] Starting new container..."
  docker run -d \
    --name queue-backend \
    --restart unless-stopped \
    --network "${BACKEND_NETWORK_NAME:-queue-network}" \
    -e TZ="$TZ" \
    -e DB_HOST="$DB_HOST" \
    -e DB_PORT="$DB_PORT" \
    -e DB_NAME="$DB_NAME" \
    -e DB_USERNAME="$DB_USERNAME" \
    -e DB_PASSWORD="$DB_PASSWORD" \
    -e REDIS_HOST="$REDIS_HOST" \
    -e REDIS_PORT="$REDIS_PORT" \
    -e REDIS_PASSWORD="$REDIS_PASSWORD" \
    -e MAIL_HOST="$MAIL_HOST" \
    -e MAIL_PORT="$MAIL_PORT" \
    -e MAIL_USERNAME="$MAIL_USERNAME" \
    -e MAIL_PASSWORD="$MAIL_PASSWORD" \
    -e MAIL_PROTOCOL="$MAIL_PROTOCOL" \
    -e MAIL_SMTP_AUTH="$MAIL_SMTP_AUTH" \
    -e MAIL_SMTP_SSL_ENABLE="$MAIL_SMTP_SSL_ENABLE" \
    -e MAIL_SMTP_STARTTLS_ENABLE="$MAIL_SMTP_STARTTLS_ENABLE" \
    -e MAIL_SMTP_SOCKET_FACTORY_CLASS="$MAIL_SMTP_SOCKET_FACTORY_CLASS" \
    -e MAIL_SMTP_SOCKET_FACTORY_PORT="$MAIL_SMTP_SOCKET_FACTORY_PORT" \
    -e JWT_SECRET="$JWT_SECRET" \
    -e APP_VERSION="$APP_VERSION" \
    -e APP_PUBLIC_HOST="$APP_PUBLIC_HOST" \
    -e APP_FRONTEND_PORT="$APP_FRONTEND_PORT" \
    -e APP_FRONTEND_BASE_URL="$APP_FRONTEND_BASE_URL" \
    -e APP_CORS_EXTRA_ORIGINS="$APP_CORS_EXTRA_ORIGINS" \
    -e LOG_PATH="$LOG_PATH" \
    -p "${BACKEND_PORT:-8080}:8080" \
    -v "$SCRIPT_DIR/backend/config/application-prod.yml:/app/config/application-prod.yml:ro" \
    --add-host host.docker.internal:host-gateway \
    "$BACKEND_IMAGE"
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

  echo "[frontend] Building dist image (version: $APP_VERSION)..."
  docker build --build-arg APP_VERSION="$APP_VERSION" -f "$SCRIPT_DIR/frontend/Dockerfile" -t "$FRONTEND_IMAGE" "$REPO_ROOT/queue-system-frontend"

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
    init_mysql_charset
    deploy_backend
    deploy_frontend
    ;;
  backend)
    deploy_backend
    ;;
  frontend)
    deploy_frontend
    ;;
  mysql-init)
    init_mysql_charset
    ;;
esac

echo ""
echo "Deployment finished."
echo "  Version       : $APP_VERSION"
echo "  Mode          : $MODE"
echo "  Backend port  : ${BACKEND_PORT:-8080}"
if [ "$MODE" != "backend" ]; then
  echo "  Frontend dist : ${FRONTEND_DIST_DIR:-}"
fi