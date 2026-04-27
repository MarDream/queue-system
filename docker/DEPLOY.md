# 排队叫号系统 Docker 部署手册

## 结论

如果你的远端服务器已经通过 Docker 跑着 MySQL、Redis、Nginx，最简单的发布方式不是“本地构建镜像再推远端仓库”，而是：

1. 服务器上保留这份源码仓库
2. 第一次把 `docker/.env` 配好
3. 以后每次更新只执行：

```bash
git pull
cd docker
chmod +x deploy-remote.sh
./deploy-remote.sh
```

这条命令会同时做两件事：

1. 重建并重启后端容器
2. 构建前端 `dist` 并直接发布到 Nginx 静态目录

这就是最符合你当前环境、同时调整最少的部署链路。

## 为什么现在这样最省事

- 前后端都收敛到 `deploy-remote.sh`
- 前端发布不再需要手工构建和手工同步 `dist`
- 远端源码更新后只保留一条部署入口
- `docker/nginx/default.conf` 继续作为模板文件保留

## 一次性配置

### 1. 复制环境文件

```bash
cd docker
cp .env.remote.example .env
```

至少填写这些字段：

```dotenv
APP_PUBLIC_HOST=你的域名或公网IP
APP_FRONTEND_BASE_URL=https://你的域名
DB_PASSWORD=你的MySQL密码
REDIS_PASSWORD=你的Redis密码
MAIL_HOST=你的SMTP地址
MAIL_USERNAME=你的发件邮箱
MAIL_PASSWORD=你的邮箱授权码
JWT_SECRET=你自己的JWT密钥，至少32位
FRONTEND_DIST_DIR=/data/www/queue-system
```

如果线上最终通过 HTTPS 域名访问，优先配置 `APP_FRONTEND_BASE_URL`，不要只依赖 `APP_PUBLIC_HOST + APP_FRONTEND_PORT`。这样后端生成的跳转地址、二维码地址、重置密码链接都会直接使用正式域名。

默认情况下，后端会通过：

- `host.docker.internal:3306` 连接 MySQL
- `host.docker.internal:6379` 连接 Redis

这要求你现有的 MySQL/Redis 容器已经映射到宿主机端口。

如果你线上不是这个端口，直接在 `.env` 改：

```dotenv
DB_HOST=host.docker.internal
DB_PORT=3306
REDIS_HOST=host.docker.internal
REDIS_PORT=6379
```

如果你的 MySQL、Redis 也和后端处于同一个 Docker 网络，更推荐直接把 `.env` 改成容器名：

```dotenv
DB_HOST=mysql
REDIS_HOST=redis
```

### 2. 配置 Nginx

参考 `docker/nginx/default.conf`。核心点只有两个：

1. `root` 指向 `FRONTEND_DIST_DIR`
2. `/api/` 代理到 Docker 网络里的 `queue-backend:8080`

因为你的 Nginx 本身也是 Docker 容器，最稳妥的做法不是代理 `127.0.0.1`，而是把 Nginx 容器接到同一个共享网络。

本仓库里的后端 Compose 网络名已经固定为 `queue-shared`，可通过 `docker/.env` 的 `BACKEND_NETWORK_NAME` 调整。

如果这个网络本来就是你服务器上已经存在的共享网络，还要在 `.env` 里额外设置：

```dotenv
BACKEND_NETWORK_EXTERNAL=true
```

如果你的 Nginx 容器还没接入这个网络，一次性执行：

```bash
docker network connect queue-shared <你的-nginx-容器名>
```

示例：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /data/www/queue-system;
    index index.html;

    location /api/ {
        proxy_pass http://queue-backend:8080/api/;
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

如果你的 Nginx 容器需要 reload，可以在 `docker/.env` 里填：

```dotenv
NGINX_CONTAINER_NAME=你的nginx容器名
```

这样 `./deploy-remote.sh` 在发布前端后会顺手 reload Nginx。

## 日常发布

以后每次源码同步到服务器后，只需要：

```bash
git pull
cd docker
./deploy-remote.sh
```

如果你只想更新后端：

```bash
./deploy-remote.sh backend
```

如果你只想更新前端：

```bash
./deploy-remote.sh frontend
```

## 当前文件职责

- `docker/deploy-remote.sh`
  远端一键部署入口，支持 `all/backend/frontend`
- `docker/docker-compose.standalone.yml`
  后端容器编排
- `docker/backend/config/application-prod.yml`
  生产配置模板，现已支持从环境变量读取
- `docker/nginx/default.conf`
  独立 Nginx 反向代理与静态站点模板

对你当前“服务器已同步源码，想快速更新”的需求，推荐始终使用：

```bash
git pull && cd docker && ./deploy-remote.sh
```
