# 排队叫号系统 - 公网云 Docker 部署手册

## 1. 适用场景

本方案适用于以下环境：

- 云服务器公网 IP：`43.155.249.87`
- 操作系统：Linux
- MySQL、Redis、Nginx 已经在云服务器上独立部署
- Docker 仅负责运行后端服务
- 前端通过独立 Nginx 托管 `dist/` 并对外提供访问

镜像命名固定为：

- 后端：`queue-backend`

镜像 tag 规则：

- 手动传入版本号时，后端使用传入的 `IMAGE_TAG`
- 未手动传入时，默认使用打包当下的时间戳（格式：`yyyyMMddHHmmss`）

## 2. 目录结构

```text
docker/
├── docker-compose.standalone.yml   # 独立部署编排（仅 backend）
├── build-and-up.sh                 # 自动生成 tag 或使用手动指定版本后构建并启动 backend
├── push-image.sh                   # 本地打包 backend 镜像并推送到远端 Registry
├── backend/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── config/
│       └── application-prod.yml    # 生产配置模板，部署前手动填写密码
├── frontend/
│   ├── Dockerfile                  # 仅用于构建并导出 dist
│   └── .dockerignore
├── nginx/
│   └── default.conf                # 独立 Nginx 配置参考模板
└── DEPLOY.md
```

## 3. 影响范围分析

### 原代码设计意图
- 前端通过 `queue-system-frontend/src/api/index.ts:4` 和 `queue-system-frontend/src/api/counter.js:3` 访问 `/api/v1`
- 开发环境由 `queue-system-frontend/vite.config.js:37` 代理 `/api` 到本地后端
- 后端通过挂载的 `docker/backend/config/application-prod.yml` 读取生产配置
- 后端 `app.ip` 与 `app.frontend.port` 会影响二维码、回显地址与 CORS

### 本次直接影响
- `docker/docker-compose.standalone.yml`
- `docker/build-and-up.sh`
- `docker/push-image.sh`
- `docker/frontend/Dockerfile`
- `docker/DEPLOY.md`

### 间接影响
- 前端发布流程改为单独构建 `dist/` 并同步到独立 Nginx 目录
- 后端通过宿主机 `8080` 端口提供 API，供独立 Nginx 反向代理
- Docker 不再承载前端运行时 Nginx

### 风险等级
- 中风险

### 风险说明
1. `host.docker.internal` 依赖 `extra_hosts: host-gateway`，要求目标 Docker 版本支持该特性
2. 若现有 MySQL/Redis 未映射到宿主机 `3306/6379`，后端将无法连接
3. 若未填写 `application-prod.yml` 中的密码和 JWT 密钥，后端会启动失败或登录鉴权异常
4. 独立 Nginx 的 `/api` 代理目标必须改成服务器实际可访问的后端地址，不能继续照搬容器内主机名

## 4. 配置步骤

### 4.1 修改后端生产配置

编辑 `docker/backend/config/application-prod.yml`，至少填写以下字段：

```yaml
spring:
  datasource:
    password: 你的MySQLRoot密码
  data:
    redis:
      password: 你的Redis密码

jwt:
  secret: 你自己的JWT密钥_至少32位
```

如宿主机端口不是默认值，还要同步修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://host.docker.internal:3306/queue_system...
  data:
    redis:
      host: host.docker.internal
      port: 6379
```

### 4.2 检查独立 Nginx 配置

前端线上访问依赖独立 Nginx，最少需要满足：

- `root` 指向前端 `dist/` 目录
- `/` 使用 `try_files $uri $uri/ /index.html`
- `/api/` 代理到后端，例如 `http://127.0.0.1:8080/api/`

可参考 `docker/nginx/default.conf`，但需要按你的线上环境改成真实地址。

示例：

```nginx
server {
    listen 80;
    server_name 43.155.249.87 zxmeng.asia www.zxmeng.asia;

    root /data/www/queue-system;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

## 5. 云服务器部署后端

### 5.1 构建并启动 backend

```bash
cd docker
chmod +x build-and-up.sh
./build-and-up.sh v1.0.3
```

或自动生成时间戳 tag：

```bash
cd docker
chmod +x build-and-up.sh
./build-and-up.sh
```

这会构建并启动：

- `queue-backend:<IMAGE_TAG>`

### 5.2 查看状态

```bash
docker compose -f docker-compose.standalone.yml ps
```

预期看到：

```text
queue-backend    Up
```

### 5.3 查看日志

```bash
docker compose -f docker-compose.standalone.yml logs -f backend
```

## 6. 构建并发布前端 dist

### 6.1 直接本机构建

```bash
cd queue-system-frontend
npm ci
npm run build
```

构建产物位于：

```text
queue-system-frontend/dist/
```

将其同步到独立 Nginx 站点目录，例如：

```bash
rsync -av --delete queue-system-frontend/dist/ /data/www/queue-system/
```

### 6.2 使用 Docker 导出 dist

前端 `docker/frontend/Dockerfile` 现在只负责构建并导出 `dist`，不再内置 Nginx。

```bash
docker build -f docker/frontend/Dockerfile -t queue-frontend-dist queue-system-frontend
docker run --rm -v "$(pwd)/frontend-dist:/output" queue-frontend-dist
```

导出的静态文件位于：

```text
./frontend-dist/
```

然后再同步到独立 Nginx 站点目录。

## 7. 本地打包并推送到远端 Registry

### 7.1 前置条件

1. 本地有 Docker 环境
2. 已登录远端 Registry：

```bash
docker login <registry地址>
```

### 7.2 推送 backend 镜像

```bash
cd docker
chmod +x push-image.sh

# 手动指定版本号
./push-image.sh 43.155.249.87:5000 v1.0.3

# 不传版本号，自动使用时间戳
./push-image.sh 43.155.249.87:5000
```

脚本执行流程：

1. 检查 Registry 登录状态
2. 本地构建 backend 镜像
3. 为镜像打上远端 tag
4. 推送到远端 Registry

### 7.3 服务器端拉取并启动 backend

在目标服务器上执行：

```bash
docker pull 43.155.249.87:5000/queue-backend:v1.0.3
docker tag 43.155.249.87:5000/queue-backend:v1.0.3 queue-backend:v1.0.3
IMAGE_TAG=v1.0.3 docker compose -f docker-compose.standalone.yml up -d backend
```

> 注意：如果 Registry 使用 HTTP（非 HTTPS），需要在服务器上配置 Docker 的 `insecure-registries`。

## 8. 验证方法

### 8.1 前端访问验证

浏览器打开：

```text
https://zxmeng.asia/
```

应能正常打开前端页面，并且刷新非首页路由不应返回 404。

### 8.2 API 代理验证

在浏览器开发者工具中检查前端请求：

- 请求路径应为 `/api/v1/...`
- 由独立 Nginx 转发到 `127.0.0.1:8080` 或你的实际后端地址
- 不应出现跨域错误

### 8.3 后端连接验证

检查后端日志确认：

- 成功连接 MySQL
- 成功连接 Redis
- 生产配置已生效

## 9. 常用命令

```bash
# 自动生成时间戳 tag 后重建并启动 backend
./build-and-up.sh

# 手动指定 tag 后重建并启动 backend
./build-and-up.sh v1.0.3

# 本地打包并推送 backend 到远端 Registry
./push-image.sh 43.155.249.87:5000 v1.0.3

# 查看 backend 日志
docker compose -f docker-compose.standalone.yml logs -f backend

# 停止 backend
docker compose -f docker-compose.standalone.yml down

# Docker 构建并导出前端 dist
docker build -f docker/frontend/Dockerfile -t queue-frontend-dist queue-system-frontend
docker run --rm -v "$(pwd)/frontend-dist:/output" queue-frontend-dist
```

## 10. 推荐部署策略结论

推荐直接使用：

- `docker/docker-compose.standalone.yml`
- `docker/build-and-up.sh`
- `docker/push-image.sh`
- `docker/backend/config/application-prod.yml`

原因：

1. 最符合你现在的线上环境：MySQL、Redis、Nginx 已独立存在，不重复编排
2. Docker 只负责后端，职责清晰，发布链路更简单
3. 前端继续保持 `/api/v1` 相对路径调用方式，不需要额外改代码
4. 独立 Nginx 统一处理静态资源、路由回退和 `/api` 反向代理
