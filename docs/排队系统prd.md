# 排队叫号系统 PRD

## 一、项目概述

### 1.1 项目背景

线下服务大厅（如银行、政务中心、医院）排队叫号场景中，需要一套高效的预约取号与叫号管理系统，解决现场等待时间长、排队秩序混乱、窗口利用率低等问题。

### 1.2 系统目标

- 支持 **预约取号** 和 **现场取号** 两种方式
- 实时排队状态展示（叫号大屏 + 用户端查询）
- 窗口人员高效叫号、办结管理
- 管理后台可视化数据统计
- 短信 / 微信消息通知

### 1.3 技术架构

| 层级 | 技术选型 |
| --- | --- |
| 前端 | Vue 3 + Vite + Element Plus |
| 后端 | Spring Boot 3 + MyBatis-Plus |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis（排队队列、实时计数） |
| 实时推送 | WebSocket（叫号大屏实时刷新） |
| 通知 | 短信（阿里云 / 腾讯云 SMS）+ 微信模板消息 |

---

## 二、用户角色与权限

| 角色 | 权限说明 |
| --- | --- |
| 普通用户 | 预约取号、现场取号、查看排队进度、查看历史记录、接收通知 |
| 窗口人员 | 叫号、重呼、跳过、办结、暂停服务、恢复服务 |
| 管理员 | 业务类型配置、窗口管理、人员分配、数据统计、系统参数配置 |

---

## 三、核心业务流程

### 3.1 取号流程

```
用户选择业务类型 → 生成排队号（规则：业务前缀+当日序号，如 A001）
→ 进入对应队列 → 返回排队信息（当前号码、前面等待人数、预估等待时间）
```

### 3.2 叫号流程

```
窗口人员点击"叫号" → 从队列取出下一位 → 大屏展示叫号信息
→ 发送通知给用户 → 用户前往指定窗口
→ 窗口人员办结 / 用户超时未到自动跳过
```

### 3.3 票号状态流转

```
WAITING（等待中）
  ├── → CALLED（已叫号）── 窗口叫号
  │       ├── → SERVING（办理中）── 用户到达窗口
  │       │       └── → COMPLETED（已办结）── 业务办理完成
  │       └── → SKIPPED（已过号）── 超时未到 / 手动跳过
  │               └── → WAITING（重新排队）── 用户申请重排（排到队尾）
  └── → CANCELLED（已取消）── 用户主动取消
```

### 3.4 预约流程

```
用户选择业务类型 + 日期 + 时段 → 校验该时段剩余名额
→ 创建预约记录 → 预约日当天自动生成排队号（优先于现场取号）
```

---

## 四、功能模块详细设计

### 4.1 用户端

#### 4.1.1 现场取号

- 选择业务类型（列表展示当前各业务等待人数）
- 确认取号，生成号码
- 展示：号码、前面等待人数、预估等待时间
- 同一手机号同一业务类型仅允许持有一个有效号码

#### 4.1.2 预约取号

- 选择业务类型 + 预约日期 + 时段（上午/下午）
- 每个时段设置名额上限（可配置）
- 预约成功后发送确认通知
- 预约当天自动生成排队号，预约号优先于现场号

#### 4.1.3 排队查询

- 输入号码或手机号查询当前排队状态
- 显示：当前号码、状态、前方等待人数、预估等待时间

#### 4.1.4 叫号大屏

- WebSocket 实时推送，无需手动刷新
- 展示内容：当前叫号号码、对应窗口、等待人数
- 语音播报："请 A008 号到 3 号窗口办理"
- 滚动展示最近 5 条叫号记录

#### 4.1.5 我的记录

- 展示用户历史取号、预约记录
- 支持按状态筛选（等待中、已完成、已取消）

### 4.2 管理端

#### 4.2.1 数据概览（Dashboard）

- 今日取号总数、已办结���、等待中数、平均等待时长、平均办理时长
- 各窗口办理量柱状图
- 各业务类型占比饼图

#### 4.2.2 业务类型管理

- 增删改查业务类型
- 字段：名称、编号前缀（如 A/B/C）、描述、是否启用
- 配置每日预约名额上限

#### 4.2.3 窗口管理

- 增删改查服务窗口
- 字段：窗口编号、名称、支持的业务类型（多对多）、当前状态（空闲/忙碌/暂停）
- 分配窗口人员

#### 4.2.4 叫号控制

- 窗口人员操作面板
- 功能按钮：叫下一位、重新呼叫、跳过当前、办结、暂停服务、恢复服务
- 显示当前正在办理的号码信息

#### 4.2.5 系统配置

- 过号超时时间（默认 5 分钟）
- 每次叫号语音播报次数（默认 2 次）
- 预约提前通知时间（默认提前 30 分钟）
- 每日号码起始序号（默认 1）

### 4.3 通知模块

| 场景 | 通知方式 | 内容示例 |
| --- | --- | --- |
| 取号成功 | 短信 + 微信 | "您已取号 A008，前面还有 5 人等待，预计等待 15 分钟" |
| 预约成功 | 短信 + 微信 | "您已预约 3 月 28 日上午 XX 业务，请当天凭手机号取号" |
| 即将叫号 | 短信 + 微信 | "您的号码 A008 前面还有 2 人，请做好准备" |
| 叫号通知 | 短信 + 微信 + 大屏语音 | "请 A008 号到 3 号窗口办理业务" |

---

## 五、API 接口设计

### 5.1 用户端接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/business-types` | 获取业务类型列表（含当前等待人数） |
| POST | `/api/v1/ticket/take` | 现场取号 |
| POST | `/api/v1/appointment/book` | 预约取号 |
| GET | `/api/v1/queue/status?ticketNo=xxx` | 查询排队状态 |
| GET | `/api/v1/queue/screen` | 叫号大屏数据（WebSocket 升级） |
| GET | `/api/v1/my/tickets?phone=xxx` | 我的排队记录 |
| POST | `/api/v1/ticket/cancel` | 取消排队 |

#### 关键接口示例

**POST /api/v1/ticket/take — 现场取号**

请求：
```json
{
  "businessTypeId": 1,
  "phone": "13800138000",
  "name": "张三"
}
```

响应：
```json
{
  "code": 200,
  "data": {
    "ticketNo": "A008",
    "businessType": "个人业务",
    "waitingCount": 5,
    "estimatedWaitMinutes": 15,
    "createdAt": "2026-03-27T10:30:00"
  }
}
```

### 5.2 管理端接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/admin/dashboard` | 今日数据概览 |
| GET/POST/PUT/DELETE | `/api/v1/admin/business-types` | 业务类型 CRUD |
| GET/POST/PUT/DELETE | `/api/v1/admin/counters` | 窗口 CRUD |
| POST | `/api/v1/admin/call/next` | 叫下一位 |
| POST | `/api/v1/admin/call/recall` | 重新呼叫 |
| POST | `/api/v1/admin/call/skip` | 跳过当前 |
| POST | `/api/v1/admin/call/complete` | 办结当前 |
| POST | `/api/v1/admin/counter/pause` | 暂停窗口服务 |
| POST | `/api/v1/admin/counter/resume` | 恢复窗口服务 |
| GET | `/api/v1/admin/stats/today` | 今日统计数据 |
| GET | `/api/v1/admin/stats/history` | 历史统计数据 |

---

## 六、数据库设计

### 6.1 业务类型表 `business_type`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| name | VARCHAR(50) | 业务名称 |
| prefix | CHAR(1) | 号码前缀，如 A、B、C |
| description | VARCHAR(200) | 业务描述 |
| daily_appointment_limit | INT | 每日预约名额上限 |
| is_enabled | TINYINT(1) | 是否启用 |
| sort_order | INT | 排序 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 6.2 窗口表 `counter`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| number | INT | 窗口编号 |
| name | VARCHAR(50) | 窗口名称 |
| status | ENUM | idle(空闲) / busy(忙碌) / paused(暂停) |
| current_ticket_id | BIGINT | 当前正在办理的票号 ID |
| operator_name | VARCHAR(50) | 当前操作员姓名 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 6.3 窗口-业务关联表 `counter_business`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| counter_id | BIGINT FK | 窗口 ID |
| business_type_id | BIGINT FK | 业务类型 ID |

### 6.4 取号记录表 `ticket`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| ticket_no | VARCHAR(10) | 排队号码，如 A008 |
| business_type_id | BIGINT FK | 业务类型 ID |
| source | ENUM | walk_in(现场) / appointment(预约) |
| phone | VARCHAR(20) | 手机号 |
| name | VARCHAR(50) | 姓名 |
| status | ENUM | waiting / called / serving / completed / skipped / cancelled |
| counter_id | BIGINT | 办理窗口 ID |
| called_at | DATETIME | 叫号时间 |
| served_at | DATETIME | 开始办理时间 |
| completed_at | DATETIME | 办结时间 |
| created_at | DATETIME | 取号时间 |

### 6.5 预约记录表 `appointment`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| business_type_id | BIGINT FK | 业务类型 ID |
| phone | VARCHAR(20) | 手机号 |
| name | VARCHAR(50) | 姓名 |
| appointment_date | DATE | 预约日期 |
| time_slot | ENUM | morning(上午) / afternoon(下午) |
| status | ENUM | pending(待取号) / taken(已取号) / cancelled(已取消) / expired(已过期) |
| ticket_id | BIGINT | 关联的取号记录 ID |
| created_at | DATETIME | 预约时间 |

### 6.6 系统配置表 `sys_config`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 主键 |
| config_key | VARCHAR(50) | 配置键 |
| config_value | VARCHAR(200) | 配置值 |
| description | VARCHAR(200) | 配置说明 |

---

## 七、非功能性需求

### 7.1 性能要求

- 取号接口响应时间 < 200ms
- 叫号大屏 WebSocket 推送延迟 < 500ms
- 系统支持同时 500 人在线排队查询

### 7.2 并发控制

- 取号使用 Redis 原子自增保证号码不重复
- 叫号操作使用分布式锁防止同一个号被多个窗口叫到

### 7.3 数据安全

- 手机号脱敏存储与展示（如 138****8000）
- 管理端接口需要 JWT 鉴权
- 用户端敏感操作需手机号验证

### 7.4 可用性

- 每日零点自动重置号码序列
- Redis 宕机时降级为数据库队列
- 关键操作（取号、叫号、办结）记录操作日志
