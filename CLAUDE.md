# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

排队叫号系统 (Queue Management System) - 一个用于服务大厅（银行、政府办公室、医院）的全栈排队取号系统。支持现场取号、柜台操作、实时显示大屏和管理后台。

**技术栈:**
- 前端: Vue 3 + Vite + Element Plus + Pinia + Vue Router
- 后端: Spring Boot 3 + MyBatis-Plus + Redis + MySQL 8.0
- 架构: Monorepo，前后端分离的文件夹结构

## 约束与规则
- 每次在接受新的需求问题时，可以反复向我提出需求确认，直至完全理解后再执行；

## 项目结构

**根路径**: `E:\Project\Vue_demo\queue-system\`

```
E:\Project\Vue_demo\queue-system\
├── queue-system-frontend/     # Vue 3 前端 (端口 5173)
│   ├── src/
│   │   ├── pages/             # 4 个主要页面: 取号机, 显示大屏, 柜台, 管理后台
│   │   ├── components/        # 可复用组件 + 管理面板
│   │   ├── router/            # Vue Router 配置
│   │   └── App.vue
│   ├── package.json
│   └── vite.config.js
├── queue-system-backend/      # Spring Boot 后端 (端口 8080)
│   ├── src/main/java/com/queue/
│   │   ├── config/            # Redis, CORS, MyBatis-Plus 配置
│   │   ├── common/            # 统一返回结果, 异常处理
│   │   ├── entity/            # 6 个实体类 (BusinessType, Counter, Ticket 等)
│   │   ├── mapper/            # MyBatis-Plus Mapper
│   │   ├── service/           # 业务逻辑层
│   │   ├── controller/        # REST API 控制器
│   │   ├── dto/               # 请求/响应 DTO
│   │   ├── enums/             # 状态枚举
│   │   └── util/              # 工具类 (PhoneUtil)
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   └── db/schema.sql      # 数据库结构 + 种子数据
│   └── pom.xml
└── docs/                      # 设计文档和实现计划
    ├── 排队系统prd.md
    └── superpowers/
        ├── specs/             # 设计规格文档
        └── plans/             # 实现计划文档
```

## 开发命令

### 前端

```bash
# 进入前端目录
cd E:\Project\Vue_demo\queue-system\queue-system-frontend

# 安装依赖
npm install

# 启动开发服务器 (http://localhost:5173)
npm run dev

# 构建生产版本
npm run build

# 预览生产构建
npm run preview
```

### 后端
#子系统说明
@backend/queue-system-backend.md

```bash
# 进入后端目录
cd E:\Project\Vue_demo\queue-system\queue-system-backend

# 编译项目
mvn clean compile

# 运行应用 (http://localhost:8080)
mvn spring-boot:run

# 打包 JAR (跳过测试)
mvn clean package -DskipTests

# 运行测试
mvn test
```

### 数据库设置

```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE queue_system;

# 执行数据库结构 (包含种子数据) - 使用绝对路径
mysql -u root -p queue_system < E:\Project\Vue_demo\queue-system\queue-system-backend\src\main\resources\db\schema.sql
```

### 前置要求

- Java 17+
- Maven 3.9+
- Node.js 18+
- MySQL 8.0 (运行在 localhost:3306)
- Redis (运行在 localhost:6379)

## 架构概览

### 后端架构 (三层)

**Controller → Service → Mapper**

- **Controllers**: REST API 端点, 请求校验, 响应格式化
- **Services**: 业务逻辑, Redis 队列操作, 事务管理
- **Mappers**: MyBatis-Plus 数据访问层

### 关键设计模式

1. **Redis 队列管理** (`QueueService`)
   - 序号生成: `queue:seq:{typeId}:{yyyyMMdd}` (原子 INCR)
   - FIFO 队列: `queue:waiting:{typeId}` (LIST: RPUSH/LPOP)
   - 等待计数: `queue:count:{typeId}` (STRING: INCR/DECR)
   - 分布式锁: `queue:lock:call:{counterId}` (SET NX EX)

2. **票号格式**: `{prefix}{sequence}` (例如: A008)
   - 前缀来自 business_type.prefix (A, B, C...)
   - 序号每日重置，零填充至 3 位

3. **状态机** (票状态):
   ```
   WAITING(等待) → CALLED(已叫) → SERVING(服务中) → COMPLETED(已完成)
              ↓          ↓
         CANCELLED(取消)  SKIPPED(跳过)
   ```

4. **多业务类型柜台叫号**:
   - 柜台支持多种业务类型 (counter_business 表)
   - callNext() 遍历所有支持的队列，选择创建时间最早的 (跨类型 FIFO)
   - 使用分布式锁防止竞态条件

### 前端架构

**4 个主要页面:**
- `/kiosk` - 用户自助取号
- `/display` - 实时显示大屏 (轮询方式，第一阶段排除 WebSocket)
- `/counter` - 柜台人员操作 (叫号、跳过、开始服务、完成服务)
- `/admin` - 管理后台仪表板 + 业务类型和柜台的增删改查

**状态管理**: Pinia stores (如已实现)

**API 客户端**: Axios，基础 URL `http://localhost:8080`

## API 端点

### 用户 API
- `GET /api/v1/business-types` - 列出已启用的业务类型及等待人数
- `POST /api/v1/ticket/take` - 现场取号
- `GET /api/v1/queue/status?ticketNo=A008` - 查询票号状态
- `POST /api/v1/ticket/cancel` - 取消等待票

### 柜台 API
- `POST /api/v1/counter/call/next` - 叫下一个号
- `POST /api/v1/counter/call/recall` - 重呼当前号
- `POST /api/v1/counter/call/skip` - 跳过当前号
- `POST /api/v1/counter/serve` - 开始服务
- `POST /api/v1/counter/complete` - 完成服务
- `POST /api/v1/counter/toggle-pause` - 切换柜台暂停/空闲状态

### 管理后台 API
- `GET/POST/PUT/DELETE /api/v1/admin/business-types` - 业务类型增删改查
- `GET/POST/PUT/DELETE /api/v1/admin/counters` - 柜台增删改查
- `GET /api/v1/queue/screen` - 显示大屏数据
- `GET /api/v1/admin/dashboard` - 仪表板统计信息

## 数据库结构

### 核心表 (第一阶段启用)

**business_type** - 业务分类 (个人业务, 对公业务等)
- 关键字段: id, name, prefix (A/B/C), is_enabled, sort_order
- 种子数据: 6 种业务类型 (A-F)

**counter** - 服务窗口
- 关键字段: id, number, name, status (idle空闲/busy繁忙/paused暂停), current_ticket_id, operator_name
- 种子数据: 6 个柜台

**counter_business** - 多对多关系表
- 关联柜台与支持的业务类型

**ticket** - 排队票号
- 关键字段: id, ticket_no, business_type_id, phone, name, status, counter_id, called_at, served_at, completed_at, created_at
- 索引: (phone, business_type_id, status), (status, business_type_id), (created_at)

### 已创建但未使用的表 (第一阶段)
- **appointment** - 用于未来预约功能
- **sys_config** - 用于未来运行时配置

## 重要实现细节

### 电话号码脱敏
- 数据库完整存储: `13800138000`
- 显示时脱敏: `138****8000`
- 所有 API 响应中使用 `PhoneUtil.mask(phone)` 进行脱敏

### 错误处理
- 统一响应格式: `Result<T>` 包含 code, message, data
- 自定义错误码在 `ResultCode` 枚举中 (40001-40006, 50001)
- `GlobalExceptionHandler` 捕获所有异常
- `BusinessException` 用于业务逻辑错误

### Redis 故障降级
当 Redis 不可用时，降级到数据库:
- 序号: `SELECT MAX(CAST(SUBSTRING(ticket_no, 2) AS UNSIGNED))` 加行锁
- 队列: `SELECT * FROM ticket WHERE status='waiting' ORDER BY created_at`
- 计数: `SELECT COUNT(*)`

### CORS 配置
- 允许的源: `http://localhost:5173`, `http://127.0.0.1:5173`
- 允许的方法: GET, POST, PUT, DELETE, OPTIONS
- 允许携带凭证: true

### 乐观锁
- 所有实体都有 `version` 字段用于乐观锁
- MyBatis-Plus 自动处理版本号递增

### 逻辑删除
- 所有实体都有 `deleted` 字段 (0=活跃, 1=已删除)
- MyBatis-Plus 配置了 logic-delete-field

## 第一阶段排除项

以下内容明确排除在第一阶段之外:
- 预约模块 (表已存在，无业务逻辑)
- WebSocket 实时推送 (使用 REST 轮询替代)
- JWT 认证 (柜台通过 counterId 参数标识)
- 短信/微信通知
- 操作审计日志
- 跳过票自动重新入队的定时任务
- sys_config 运行时读取 (使用硬编码默认值)

## 常见开发任务

### 添加新的 API 端点

1. 在 `dto/` 目录创建 DTO (请求/响应)
2. 在 `service/` 目录的 Service 接口中添加方法
3. 在 `service/impl/` 目录实现方法
4. 在 `controller/` 目录添加控制器方法
5. 使用 curl 或 Postman 测试

### 修改数据库结构

1. 更新 `E:\Project\Vue_demo\queue-system\queue-system-backend\src\main\resources\db\schema.sql`
2. 删除并重建数据库: `DROP DATABASE queue_system; CREATE DATABASE queue_system;`
3. 重新执行数据库结构: `mysql -u root -p queue_system < E:\Project\Vue_demo\queue-system\queue-system-backend\src\main\resources\db\schema.sql`
4. 更新 `queue-system-backend\src\main\java\com\queue\entity\` 中对应的实体类
5. 重启 Spring Boot 应用

### 添加新的前端页面

1. 在 `src/pages/` 目录创建 Vue 组件
2. 在 `src/router/index.js` 中添加路由
3. 如需要，添加导航链接

## 测试

### 手动 API 测试

```bash
# 获取业务类型
curl http://localhost:8080/api/v1/business-types

# 取号
curl -X POST http://localhost:8080/api/v1/ticket/take \
  -H "Content-Type: application/json" \
  -d '{"businessTypeId":1,"phone":"13800138000","name":"张三"}'

# 查询状态
curl "http://localhost:8080/api/v1/queue/status?ticketNo=A001"

# 叫下一个号 (1号柜台)
curl -X POST http://localhost:8080/api/v1/counter/call/next \
  -H "Content-Type: application/json" \
  -d '{"counterId":1}'
```

## 配置

### 后端配置 (application-dev.yml)

- MySQL: `jdbc:mysql://localhost:3306/queue_system`
- Redis: `localhost:6379`
- 服务器端口: `8080`
- 队列配置: `avg-service-minutes: 3`, `ticket-number-padding: 3`

### 前端配置 (vite.config.js)

- 开发服务器端口: `5173`
- API 代理: 开发期间如需解决 CORS 问题可配置

## Git 工作流

- 主分支: `main`
- 当前分支: `master` (应合并到 main)
- 提交格式: `feat(scope): description` 并附带 Co-Authored-By 尾部
- 设计文档在 `docs/superpowers/specs/`
- 实现计划在 `docs/superpowers/plans/`
