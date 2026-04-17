# 排队叫号系统 - 公网云 Docker 部署手册

## 1. 适用场景

本方案适用于以下环境：

- 云服务器公网 IP：`43.155.249.87`
- 操作系统：Linux
- MySQL 和 Redis 已经在云服务器上通过 Docker 独立运行
- 本次只部署前端和后端两个服务
- 前端通过公网地址 `http://43.155.249.87` 访问

镜像命名固定为：

- 前端：`queue-frontend`
- 后端：`queue-backend`

镜像 tag 规则：

- 手动传入版本号时，前后端统一使用传入的 `IMAGE_TAG`
- 未手动传入时，默认使用打包当下的时间戳（格式：`yyyyMMddHHmmss`）

## 2. 目录结构

```
docker/
├── docker-compose.standalone.yml   # 独立部署编排（推荐）
├── build-and-up.sh                 # 自动生成 tag 或使用手动指定版本后构建并启动
├── push-image.sh                   # 本地打包镜像并推送到远端 Registry
├── backend/
│   ├── Dockerfile
│   ├── .dockerignore
│   └── config/
│       └── application-prod.yml    # 生产配置模板，部署前手动填写密码
├── frontend/
│   ├── Dockerfile
│   └── .dockerignore
├── nginx/
│   └── default.conf                # 前端 Nginx 配置
└── DEPLOY.md
```

## 3. 影响范围分析

### 原代码设计意图
- 前端通过 `queue-system-frontend/src/api/index.ts:4` 和 `queue-system-frontend/src/api/counter.js:3` 固定访问 `/api/v1`
- 开发环境由 `queue-system-frontend/vite.config.js:37` 代理 `/api` 到本地后端
- 后端通过 `queue-system-backend/src/main/resources/application.yml:10` 默认启用 `dev` 配置
- 后端 `app.ip` 与 `app.frontend.port` 由 `queue-system-backend/src/main/java/com/queue/config/ServerConfig.java:19` 读取，影响二维码、回显地址与 CORS
- JWT 密钥由 `queue-system-backend/src/main/java/com/queue/util/JwtUtil.java:18` 读取

### 本次直接影响
- `docker/backend/Dockerfile`
- `docker/nginx/default.conf`
- `docker/docker-compose.standalone.yml`
- `docker/build-and-up.sh`
- `docker/backend/config/application-prod.yml`
- `docker/DEPLOY.md`

### 间接影响
- 前端公网访问改为 Nginx 承载静态资源并转发 `/api`
- 后端生产环境将通过外部挂载配置连接宿主机上的 MySQL/Redis
- 生产环境不再依赖 `.env` 注入数据库和 Redis 密码，而是改为挂载配置文件

### 风险等级
- 中风险

### 风险说明
1. `host.docker.internal` 依赖 `extra_hosts: host-gateway`，要求目标 Docker 版本支持该特性
2. 若现有 MySQL/Redis 容器未映射到宿主机 `3306/6379`，后端将无法连接
3. 若未填写 `application-prod.yml` 中的密码和 JWT 密钥，后端会启动失败或登录鉴权异常
4. 当前 Nginx `server_name` 固定为 `43.155.249.87`，若后续改域名需同步调整

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

### 4.2 检查前端 Nginx 配置

`docker/nginx/default.conf` 已经配置：

- 监听 `80`
- `server_name 43.155.249.87`
- `/api/` 代理到 `http://queue-backend:8080/api/`
- `/` 使用 `try_files` 支持单页路由回退

如果后续公网地址不是 `43.155.249.87`，需要同步修改 `server_name`。

## 5. 云服务器打包与启动

你要求实际打包动作在云服务器进行，因此建议把整个项目上传到云服务器后，在项目根目录执行。

### 5.1 构建并启动服务

推荐统一通过脚本执行，这样可以保证同一次执行中的前后端镜像 tag 完全一致。

#### 方式一：手动指定版本号

```bash
cd docker
chmod +x build-and-up.sh
./build-and-up.sh v1.0.3
```

这会构建并启动：

- `queue-backend:v1.0.3`
- `queue-frontend:v1.0.3`

#### 方式二：不传版本号，自动使用当前时间戳

```bash
cd docker
chmod +x build-and-up.sh
./build-and-up.sh
```

这会自动生成类似如下 tag：

- `queue-backend:20260416153045`
- `queue-frontend:20260416153045`

### 5.2 查看状态

```bash
docker compose -f docker-compose.standalone.yml ps
```

预期至少看到：

```text
queue-backend    Up
queue-frontend   Up
```

### 5.3 查看日志

```bash
docker compose -f docker-compose.standalone.yml logs -f backend
docker compose -f docker-compose.standalone.yml logs -f frontend
```

## 6. 验证方法

### 6.1 访问验证

浏览器打开：

```text
http://43.155.249.87
```

应能正常打开前端页面。

### 6.2 API 代理验证

在浏览器开发者工具中检查前端请求：

- 请求路径应为 `/api/v1/...`
- 由 Nginx 转发到 `queue-backend:8080`
- 不应出现跨域错误

### 6.3 后端连接验证

检查后端日志确认：

- 成功连接 MySQL
- 成功连接 Redis
- 生产配置已生效

## 7. 本地打包并推送到远端 Registry

如果你有一台远端 Docker Registry 服务，可以在本地构建好镜像后直接推送，服务器端只需要拉取即可。

### 7.1 前置条件

1. 本地有 Docker 环境
2. 已登录远端 Registry：

```bash
docker login <registry地址>
```

### 7.2 推送镜像

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
2. 本地构建前后端镜像
3. 为镜像打上远端 tag（如 `43.155.249.87:5000/queue-frontend:v1.0.3`）
4. 推送到远端 Registry

### 7.3 服务器端拉取并启动

在目标服务器上执行：

```bash
# 拉取镜像
docker pull 43.155.249.87:5000/queue-frontend:v1.0.3
docker pull 43.155.249.87:5000/queue-backend:v1.0.3

# 给本地镜像补一个短名 tag（compose 里用的名字）
docker tag 43.155.249.87:5000/queue-frontend:v1.0.3 queue-frontend:v1.0.3
docker tag 43.155.249.87:5000/queue-backend:v1.0.3 queue-backend:v1.0.3

# 启动服务
IMAGE_TAG=v1.0.3 docker compose -f docker-compose.standalone.yml up -d
```

> 注意：如果 Registry 使用 HTTP（非 HTTPS），需要在服务器上配置 Docker 的 `insecure-registries`，
> 编辑 `/etc/docker/daemon.json`：
> ```json
> { "insecure-registries": ["43.155.249.87:5000"] }
> ```
> 然后重启 Docker：`sudo systemctl restart docker`

## 8. 常用命令

```bash
# 自动生成时间戳 tag 后重建并启动
./build-and-up.sh

# 手动指定 tag 后重建并启动
./build-and-up.sh v1.0.3

# 本地打包并推送到远端 Registry
./push-image.sh 43.155.249.87:5000 v1.0.3

# 停止
docker compose -f docker-compose.standalone.yml down

# 查看后端日志
docker compose -f docker-compose.standalone.yml logs -f backend

# 查看前端日志
docker compose -f docker-compose.standalone.yml logs -f frontend
```

## 9. 推荐部署策略结论

推荐直接使用：

- `docker/docker-compose.standalone.yml`
- `docker/backend/config/application-prod.yml`
- `docker/nginx/default.conf`

原因：

1. 最符合你现在的云环境：MySQL/Redis 已存在，不重复编排
2. 敏感信息不写死在镜像里，而是通过挂载外部配置文件提供
3. 前端直接暴露公网 `80` 端口，公网 IP 可直接访问
4. 保持前端 `/api/v1` 现有调用方式不变，改动最小
