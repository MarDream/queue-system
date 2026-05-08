# CLAUDE.md

排队叫号系统 — 服务大厅排队取号全栈系统。

技术栈: Vue 3 + Vite + Element Plus / Spring Boot 3 + MyBatis-Plus + Redis + MySQL 8.0

## 约束

- 新需求必须反复确认理解后再执行

## 项目结构

```
queue-system-frontend/  # 端口 5173
  src/pages/            # kiosk取号 | display大屏 | counter柜台 | admin管理
  src/components/       # 可复用组件 + 管理面板
queue-system-backend/   # 端口 8080
  src/main/java/com/queue/
    controller/ service/ mapper/ entity/ dto/ enums/ util/
  src/main/resources/db/schema.sql  # DDL + 种子数据
docs/
  完整需求文档.md       # 主文档入口
  topics/               # 专题文档
  archive/              # 归档
```

## 核心架构决策

**Redis 队列模型**: 序号 `queue:seq:{typeId}:{date}` | FIFO `queue:waiting:{typeId}` | 计数 `queue:count:{typeId}` | 分布式锁 `queue:lock:call:{counterId}`
**票号格式**: `{prefix}{seq}` 如 A008，前缀取 business_type.prefix，序号每日重置
**状态机**: WAITING→CALLED→SERVING→COMPLETED，可分叉至 CANCELLED / SKIPPED
**多业务柜台叫号**: counter_business 多对多，callNext() 跨类型 FIFO 取最早票 + 分布式锁防竞态
**Redis 故障降级**: 退回 DB 查询（行锁序号 + status='waiting' 排序 + COUNT）
- **SQL维护**: 所有SQL变更整合到 database/init.sql，不新增独立SQL文件

## 数据库核心表

| 表 | 用途 | 关键字段 |
|---|---|---|
| business_type | 业务分类 | prefix(A-F), is_enabled, sort_order |
| counter | 服务窗口 | status(idle/busy/paused), current_ticket_id |
| counter_business | 柜台-业务多对多 | - |
| ticket | 排队票号 | ticket_no, phone, status, counter_id, 时间戳×4 |

通用: 所有表有 version(乐观锁) + deleted(逻辑删除)

## 实现约束

- 电话脱敏: PhoneUtil.mask()，DB 存完整，API 返回脱敏
- 统一响应: Result\<T\> + ResultCode 枚举 + GlobalExceptionHandler
- CORS: 允许 localhost:5173

## 第一阶段排除

预约模块 | WebSocket(用轮询) | JWT(用counterId) | 短信/微信通知 | 审计日志 | 跳过票自动重入队 | sys_config 运行时读取

## 开发命令

```bash
# 前端: queue-system-frontend/
npm install && npm run dev        # 开发
npm run build                     # 构建

# 后端: queue-system-backend/
mvn spring-boot:run              # 开发
mvn clean package -DskipTests    # 打包

# 数据库
mysql -u root -p queue_system < src/main/resources/db/schema.sql
```

前置: Java 17+ / Maven 3.9+ / Node 18+ / MySQL 8.0(3306) / Redis(6379)

## Git

主分支 main | 提交格式 feat(scope): desc | 文档入口 docs/完整需求文档.md
