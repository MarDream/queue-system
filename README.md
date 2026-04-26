# queue-system（排队叫号系统）

面向线下服务大厅（政务/银行/医院等）的排队取号与叫号管理系统：用户扫码预约/取号并实时查询排队进度；窗口人员叫号、跳过、办结；管理员配置区域、业务类型、窗口与人员，并查看统计数据。

## 功能概览

- 用户端：扫码取号/预约、查询排队状态、查看我的记录、取消排队
- 窗口端：叫下一位、重呼、开始办理、跳过、办结、暂停/恢复、过号票重新激活
- 管理端：区域/业务类型/窗口/人员管理、菜单与权限（角色/用户级）、统计分析（含导出）
- 账号体系：注册（非超级管理员角色）、管理员激活、邮箱验证码找回/重置密码

## 技术栈

- 后端：Spring Boot 3.3.x + MyBatis-Plus（JDK 21）
- 前端：Vue 3 + Vite + Element Plus + Pinia
- 数据：MySQL 8.0
- 缓存：Redis
- 部署：Docker（参考 `docker/`）

## 仓库结构

```text
queue-system/
├── queue-system-backend/          # Spring Boot 后端
├── queue-system-frontend/         # Vue3 前端
├── docker/                        # Docker 构建/部署脚本与 Nginx 配置示例
└── docs/                          # PRD 等文档
```

## 本地开发

### 1) 启动 MySQL / Redis

使用本地 MySQL/Redis，或用 Docker 自行启动。

后端数据库脚本：`queue-system-backend/src/main/resources/db/schema.sql`

### 2) 启动后端

前置条件：JDK 21、Maven

配置参考：`queue-system-backend/src/main/resources/application-dev-example.yml`

```bash
cd queue-system-backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

默认 API 前缀：`/api/v1`

### 3) 启动前端

前置条件：Node.js 18+（推荐 20+）

```bash
cd queue-system-frontend
npm ci
npm run dev
```

## 部署

- Docker 部署说明：`docker/DEPLOY.md`
- 生产配置模板：`docker/backend/config/application-prod.yml`
