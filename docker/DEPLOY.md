# 排队叫号系统 - Docker 部署手册

## 1. 前置条件

- Docker 20.10+
- Docker Compose V2（`docker compose` 命令）
- 服务器至少 2GB 内存、10GB 磁盘空间

## 2. 目录结构

```
docker/
├── docker-compose.yml              # 服务编排
├── .env                            # 环境变量（敏感配置，手动创建）
├── .env.example                    # 环境变量模板
├── backend/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── config/
│       ├── application.yml         # 外部挂载配置（可修改）
│       └── application-prod.yml    # 生产默认配置（打入镜像）
├── frontend/
│   ├── Dockerfile
│   └── .dockerignore
├── nginx/
│   └── default.conf                # Nginx 配置（可修改）
├── mysql/
│   └── init/
│       └── schema.sql              # 数据库初始化脚本
└── DEPLOY.md                       # 本文件
```

## 3. 配置步骤

### 3.1 创建环境变量文件

```bash
cd docker
cp .env.example .env
```

### 3.2 修改 .env

必改项：
```env
# 数据库密码（务必修改）
MYSQL_ROOT_PASSWORD=你的数据库密码

# JWT 密钥（务必修改，至少 32 个字符）
JWT_SECRET=你的JWT密钥

# 应用 IP（必须设为服务器外部可达 IP 或域名）
APP_IP=192.168.1.100
```

可选项：
```env
# 前端对外端口（默认 80）
FRONTEND_PORT=80

# CORS 额外允许源（如果有多个访问地址）
APP_CORS_ORIGINS=http://queue.example.com,http://10.0.0.50
```

### 3.3 修改后端配置（可选）

编辑 `backend/config/application.yml`，可修改：
- `app.ip` — 应用 IP（也可通过 .env 的 APP_IP 设置）
- `app.frontend.port` — 前端端口（默认 80）
- `app.cors.extra-origins` — 额外 CORS 源

### 3.4 修改 Nginx 配置（可选）

编辑 `nginx/default.conf`，一般无需修改。如需添加 HTTPS，在此文件中配置 SSL 证书。

## 4. 构建与启动

### 4.1 首次构建并启动

```bash
cd docker
docker compose up -d --build
```

首次启动约需 3-5 分钟（Maven 下载依赖、npm 安装包、MySQL 初始化数据）。

### 4.2 查看服务状态

```bash
docker compose ps
```

预期输出（全部 healthy/running）：
```
NAME             STATUS
queue-mysql      Up (healthy)
queue-redis      Up (healthy)
queue-backend    Up
queue-frontend   Up
```

### 4.3 查看日志

```bash
# 所有服务日志
docker compose logs -f

# 仅后端日志
docker compose logs -f backend

# 仅 MySQL 初始化日志
docker compose logs mysql
```

## 5. 验证

### 5.1 前端访问

浏览器打开 `http://<APP_IP>` 或 `http://<APP_IP>:<FRONTEND_PORT>`，应看到登录页面。

### 5.2 登录测试

默认管理员账号：
- 用户名：`admin`
- 密码：`admin123`

### 5.3 功能验证

1. 登录后进入管理后台
2. 检查区域管理、窗口管理、用户管理页面正常
3. 取号页面正常取号
4. 叫号大屏正常显示

## 6. 常用运维命令

```bash
# 停止所有服务
docker compose down

# 停止并清除数据卷（重置数据库）
docker compose down -v

# 重新构建某个服务
docker compose up -d --build backend

# 重启某个服务
docker compose restart backend

# 查看资源使用
docker stats

# 进入后端容器
docker compose exec backend sh

# 进入 MySQL 容器
docker compose exec mysql mysql -u root -p queue_system

# 备份数据库
docker compose exec mysql mysqldump -u root -p queue_system > backup.sql

# 恢复数据库
docker compose exec -T mysql mysql -u root -p queue_system < backup.sql
```

## 7. 仅重建某个服务

当只修改了后端代码时，无需重建全部：

```bash
docker compose up -d --build backend
```

仅修改了前端代码时：

```bash
docker compose up -d --build frontend
```

修改了 Nginx 配置或 application.yml 时，无需重建，重启即可：

```bash
docker compose restart frontend   # Nginx 配置变更
docker compose restart backend    # application.yml 变更
```

## 8. 注意事项

1. **APP_IP 必须设置**：二维码生成、CORS、重定向等功能依赖此 IP，留空会导致这些功能异常
2. **首次启动慢**：MySQL 需要初始化数据库和种子数据，约 30-60 秒，后续启动很快
3. **数据持久化**：MySQL 和 Redis 数据存储在 Docker volumes 中，`docker compose down` 不会删除数据，`docker compose down -v` 才会
4. **端口冲突**：如果 80 端口被占用，修改 `.env` 中的 `FRONTEND_PORT`
5. **Google Fonts**：前端使用了 Google Fonts，内网环境需确保能访问或改为本地字体
6. **密码安全**：`.env` 文件包含敏感信息，已通过 `.gitignore` 排除，不要提交到版本库
