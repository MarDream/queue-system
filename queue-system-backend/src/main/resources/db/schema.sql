-- =============================================
-- 排队叫号系统数据库建表脚本（重建版）
-- 角色管理模块
-- =============================================

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `sys_role_button`;
DROP TABLE IF EXISTS `sys_role_menu`;
DROP TABLE IF EXISTS `sys_user_button`;
DROP TABLE IF EXISTS `sys_user_menu`;
DROP TABLE IF EXISTS `sys_user_menu_sort`;
DROP TABLE IF EXISTS `counter_business`;
DROP TABLE IF EXISTS `counter_operator`;
DROP TABLE IF EXISTS `ticket`;
DROP TABLE IF EXISTS `appointment`;
DROP TABLE IF EXISTS `qrcode_record`;
DROP TABLE IF EXISTS `counter`;
DROP TABLE IF EXISTS `region_business`;
DROP TABLE IF EXISTS `business_type`;
DROP TABLE IF EXISTS `region`;
DROP TABLE IF EXISTS `sys_button`;
DROP TABLE IF EXISTS `sys_menu`;
DROP TABLE IF EXISTS `sys_user`;
DROP TABLE IF EXISTS `sys_config`;
DROP TABLE IF EXISTS `sys_role`;
SET FOREIGN_KEY_CHECKS = 1;

-- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `code` VARCHAR(20) NOT NULL COMMENT '角色编码（唯一标识）',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `description` VARCHAR(200) COMMENT '角色描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `type` VARCHAR(20) NOT NULL DEFAULT 'SYSTEM' COMMENT '类型：SYSTEM=内置，CUSTOM=自定义',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- 角色菜单关联表（关联角色ID）
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `role_code` VARCHAR(20) NOT NULL COMMENT '角色编码（冗余字段，方便查询）',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`),
    INDEX `idx_role_code` (`role_code`),
    INDEX `idx_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- 角色按钮关联表（关联角色ID）
CREATE TABLE IF NOT EXISTS `sys_role_button` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `role_code` VARCHAR(20) NOT NULL COMMENT '角色编码（冗余字段，方便查询）',
    `button_id` BIGINT NOT NULL COMMENT '按钮ID',
    UNIQUE KEY `uk_role_button` (`role_id`, `button_id`),
    INDEX `idx_role_code` (`role_code`),
    INDEX `idx_button` (`button_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色按钮关联表';

-- 行政区域表（市/镇/街道三级树形结构）
CREATE TABLE IF NOT EXISTS `region` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `region_code` VARCHAR(20) NOT NULL COMMENT '区域编码',
    `region_name` VARCHAR(50) NOT NULL COMMENT '区域名称',
    `level` VARCHAR(20) NOT NULL COMMENT '层级：city=市级，town=镇/区级，street=街道级',
    `parent_id` BIGINT COMMENT '父级区域ID（市级为NULL）',
    `sort_order` INT DEFAULT 0 COMMENT '显示排序顺序',
    `announcement_text` VARCHAR(500) COMMENT '叫号大屏公告内容',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_region_code` (`region_code`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行政区域表';

-- 业务类型表（全局定义，不绑定区域）
CREATE TABLE IF NOT EXISTS `business_type` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '业务类型名称',
    `prefix` VARCHAR(5) NOT NULL COMMENT '票号前缀代码',
    `description` VARCHAR(200) COMMENT '业务类型描述',
    `daily_appointment_limit` INT DEFAULT 50 COMMENT '每日预约限额',
    `is_enabled` TINYINT(1) DEFAULT 1 COMMENT '全局是否启用：1=是，0=否',
    `sort_order` INT DEFAULT 0 COMMENT '显示排序顺序',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_name` (`name`),
    UNIQUE KEY `uk_prefix` (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务类型表（全局）';

-- 区域-业务类型关联表（区域级别的业务启用状态）
CREATE TABLE IF NOT EXISTS `region_business` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `region_id` BIGINT NOT NULL COMMENT '区域ID',
    `business_type_id` BIGINT NOT NULL COMMENT '业务类型ID',
    `is_enabled` TINYINT(1) DEFAULT 1 COMMENT '该区域是否启用此业务：1=是，0=否',
    `sort_order` INT DEFAULT 0 COMMENT '该区域内的排序顺序',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_region_business` (`region_id`, `business_type_id`),
    INDEX `idx_region` (`region_id`),
    INDEX `idx_business` (`business_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域-业务类型关联表';

-- 服务窗口表
CREATE TABLE IF NOT EXISTS `counter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `region_id` BIGINT NOT NULL COMMENT '所属区域ID',
    `number` INT NOT NULL COMMENT '窗口编号',
    `name` VARCHAR(50) NOT NULL COMMENT '窗口名称',
    `status` VARCHAR(20) DEFAULT 'idle' COMMENT '窗口状态：idle=空闲，busy=忙碌，paused=暂停',
    `current_ticket_id` BIGINT COMMENT '当前正在服务的票号ID',
    `operator_name` VARCHAR(50) COMMENT '窗口业务员姓名',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_region_number` (`region_id`, `number`),
    INDEX `idx_region` (`region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务窗口表';

-- 窗口与业务类型关联表
CREATE TABLE IF NOT EXISTS `counter_business` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `counter_id` BIGINT NOT NULL COMMENT '窗口ID',
    `business_type_id` BIGINT NOT NULL COMMENT '业务类型ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_counter_business` (`counter_id`, `business_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='窗口与业务类型关联表';

-- 排队票号表
CREATE TABLE IF NOT EXISTS `ticket` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `region_id` BIGINT NOT NULL COMMENT '所属区域ID',
    `ticket_no` VARCHAR(20) NOT NULL COMMENT '票号',
    `business_type_id` BIGINT NOT NULL COMMENT '业务类型ID',
    `source` VARCHAR(20) DEFAULT 'online' COMMENT '来源：online=扫码取号，appointment=预约，manual=管理员代录',
    `phone` VARCHAR(20) COMMENT '手机号码（脱敏后）',
    `name` VARCHAR(50) COMMENT '客户姓名',
    `status` VARCHAR(20) DEFAULT 'waiting' COMMENT '状态：waiting=等待中，called=已叫号，serving=服务中，completed=已完成，skipped=已跳过，cancelled=已取消',
    `counter_id` BIGINT COMMENT '分配的窗口ID',
    `called_at` DATETIME COMMENT '被叫时间',
    `served_at` DATETIME COMMENT '开始服务时间',
    `completed_at` DATETIME COMMENT '服务完成时间',
    `skip_type` VARCHAR(20) COMMENT '过号来源类型：manual=人工跳过，system=系统过号',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    INDEX `idx_region` (`region_id`),
    INDEX `idx_ticket_phone_type_status` (`phone`, `business_type_id`, `status`),
    INDEX `idx_ticket_status_type` (`status`, `business_type_id`),
    INDEX `idx_ticket_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排队票号表';

-- 预约表
CREATE TABLE IF NOT EXISTS `appointment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `business_type_id` BIGINT NOT NULL COMMENT '业务类型ID',
    `phone` VARCHAR(20) COMMENT '手机号码',
    `name` VARCHAR(50) COMMENT '客户姓名',
    `appointment_date` DATE NOT NULL COMMENT '预约日期',
    `time_slot` VARCHAR(20) COMMENT '预约时段：morning=上午，afternoon=下午',
    `status` VARCHAR(20) COMMENT '状态：pending=待确认，confirmed=已确认，cancelled=已取消',
    `ticket_id` BIGINT COMMENT '关联的票号ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `config_key` VARCHAR(50) NOT NULL COMMENT '配置项键名',
    `config_value` VARCHAR(200) COMMENT '配置项键值',
    `description` VARCHAR(200) COMMENT '配置项描述',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- 窗口与操作员关联表
CREATE TABLE IF NOT EXISTS `counter_operator` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `counter_id` BIGINT NOT NULL COMMENT '窗口ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_counter_user` (`counter_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='窗口与操作员关联表';

-- 二维码记录表
CREATE TABLE IF NOT EXISTS `qrcode_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `region_id` BIGINT NOT NULL COMMENT '区域ID',
    `region_code` VARCHAR(20) NOT NULL COMMENT '区域代码（冗余，方便查询）',
    `region_name` VARCHAR(50) NOT NULL COMMENT '区域名称',
    `url` VARCHAR(500) NOT NULL COMMENT '二维码URL',
    `created_by` VARCHAR(50) COMMENT '创建人姓名',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_region` (`region_id`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二维码记录表';

-- 系统用户表
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码（BCrypt加密）',
    `name` VARCHAR(50) COMMENT '真实姓名',
    `email` VARCHAR(100) COMMENT '邮箱（用于找回密码）',
    `role` VARCHAR(20) NOT NULL COMMENT '角色编码（关联sys_role.code）',
    `region_id` BIGINT COMMENT '管辖区域ID（区域管理员必填）',
    `region_code` VARCHAR(20) COMMENT '区域代码（冗余，方便登录上下文查询）',
    `status` TINYINT(1) DEFAULT 0 COMMENT '状态：0=待激活，1=启用，2=禁用',
    `activated_by` BIGINT COMMENT '激活人用户ID',
    `activated_at` DATETIME COMMENT '激活时间',
    `last_login_at` DATETIME COMMENT '最后登录时间',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    INDEX `idx_region` (`region_id`),
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 系统菜单表
CREATE TABLE `sys_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '菜单名称',
    `path` VARCHAR(100) NOT NULL COMMENT '路由路径',
    `icon` VARCHAR(50) COMMENT '图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `parent_id` BIGINT COMMENT '父级菜单ID',
    `type` VARCHAR(10) NOT NULL DEFAULT 'menu' COMMENT '类型：menu=菜单，page=页面',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_path` (`path`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='菜单表';

-- 系统按钮表
CREATE TABLE `sys_button` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `menu_id` BIGINT NOT NULL COMMENT '所属菜单ID',
    `name` VARCHAR(50) NOT NULL COMMENT '按钮名称',
    `code` VARCHAR(50) NOT NULL COMMENT '按钮编码',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_menu_code` (`menu_id`, `code`),
    INDEX `idx_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按钮权限表';

-- 用户菜单关联表
CREATE TABLE `sys_user_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    UNIQUE KEY `uk_user_menu` (`user_id`, `menu_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户菜单权限表';

-- 用户按钮关联表
CREATE TABLE `sys_user_button` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `button_id` BIGINT NOT NULL COMMENT '按钮ID',
    UNIQUE KEY `uk_user_button` (`user_id`, `button_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户按钮权限表';

-- 用户菜单排序表
CREATE TABLE `sys_user_menu_sort` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    `sort_order` INT DEFAULT 0 COMMENT '排序顺序',
    UNIQUE KEY `uk_user_menu` (`user_id`, `menu_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户菜单排序偏好表';

-- =============================================
-- 初始化内置角色数据
-- =============================================
INSERT INTO `sys_role` (`code`, `name`, `description`, `sort_order`, `type`) VALUES
('SUPER_ADMIN', '超级管理员', '系统超级管理员，拥有所有权限', 1, 'SYSTEM'),
('REGION_ADMIN', '区域管理员', '区域管理员，可管理本区域用户和业务', 2, 'SYSTEM'),
('WINDOW_OPERATOR', '窗口操作员', '窗口操作员，仅可使用窗口工作台', 3, 'SYSTEM');

-- =============================================
-- 初始化超级管理员（密码：admin123，BCrypt加密）
-- =============================================
INSERT INTO `sys_user` (`username`, `password`, `name`, `email`, `role`, `region_id`, `region_code`, `status`, `activated_at`) VALUES
('admin', '$2a$10$YvDcCi7FNwYahz2JkUwYqOcugV1mocqOUb2EZvWEsihhcercZ4bUG', '超级管理员', NULL, 'SUPER_ADMIN', NULL, NULL, 1, NOW());

UPDATE `sys_user`
SET `region_id` = NULL, `region_code` = NULL
WHERE `role` = 'SUPER_ADMIN';

-- =============================================
-- 初始化系统菜单数据
-- =============================================
INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type) VALUES
('首页', '/home', 'HomeFilled', 1, NULL, 'menu'),
('预约', '/appointment', 'Calendar', 2, NULL, 'menu'),
('大屏', '/display', 'Monitor', 3, NULL, 'menu'),
('窗口', '/counter', 'OfficeBuilding', 4, NULL, 'menu'),
('管理', '/admin', 'Setting', 5, NULL, 'menu');

SET @admin_menu_id = (SELECT id FROM sys_menu WHERE path = '/admin' LIMIT 1);

INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type)
SELECT '区域管理', '/admin?tab=region', 'MapLocation', 6, @admin_menu_id, 'menu'
FROM sys_menu WHERE path = '/admin' LIMIT 1;

INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type)
SELECT '用户管理', '/admin?tab=users', 'UserFilled', 7, @admin_menu_id, 'menu'
FROM sys_menu WHERE path = '/admin' LIMIT 1;

INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type)
SELECT '业务管理', '/admin?tab=biz', 'Tickets', 8, @admin_menu_id, 'menu'
FROM sys_menu WHERE path = '/admin' LIMIT 1;

INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type)
SELECT '窗口管理', '/admin?tab=counters', 'OfficeBuilding', 9, @admin_menu_id, 'menu'
FROM sys_menu WHERE path = '/admin' LIMIT 1;

INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type)
SELECT '二维码', '/admin?tab=qrcode', 'Cellphone', 10, @admin_menu_id, 'menu'
FROM sys_menu WHERE path = '/admin' LIMIT 1;

INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type)
SELECT '菜单管理', '/admin?tab=menu', 'Menu', 11, @admin_menu_id, 'menu'
FROM sys_menu WHERE path = '/admin' LIMIT 1;

INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type)
SELECT '统计分析', '/admin?tab=statistics', 'DataAnalysis', 12, @admin_menu_id, 'menu'
FROM sys_menu WHERE path = '/admin' LIMIT 1;

-- =============================================
-- 初始化系统按钮数据
-- =============================================
SET @counter_menu_id = (SELECT id FROM sys_menu WHERE path = '/counter' LIMIT 1);

-- 菜单管理按钮权限（用于前端 v-permission: btn:add / btn:edit / btn:delete）
SET @menu_manage_id = (SELECT id FROM sys_menu WHERE path = '/admin?tab=menu' LIMIT 1);

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @menu_manage_id, '新增菜单', 'btn:add', 1
FROM sys_menu WHERE path = '/admin?tab=menu' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @menu_manage_id, '编辑菜单', 'btn:edit', 2
FROM sys_menu WHERE path = '/admin?tab=menu' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @menu_manage_id, '删除菜单', 'btn:delete', 3
FROM sys_menu WHERE path = '/admin?tab=menu' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @counter_menu_id, '叫号', 'btn_call', 1
FROM sys_menu WHERE path = '/counter' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @counter_menu_id, '完成', 'btn_complete', 2
FROM sys_menu WHERE path = '/counter' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @counter_menu_id, '暂停', 'btn_pause', 3
FROM sys_menu WHERE path = '/counter' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @counter_menu_id, '跳过', 'btn_skip', 4
FROM sys_menu WHERE path = '/counter' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @counter_menu_id, '重新叫号', 'btn_recall', 5
FROM sys_menu WHERE path = '/counter' LIMIT 1;

SET @stats_menu_id = (SELECT id FROM sys_menu WHERE path = '/admin?tab=statistics' LIMIT 1);

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @stats_menu_id, '导出', 'btn:export', 1
FROM sys_menu WHERE path = '/admin?tab=statistics' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @stats_menu_id, '刷新', 'btn:refresh', 2
FROM sys_menu WHERE path = '/admin?tab=statistics' LIMIT 1;

INSERT INTO sys_button (menu_id, name, code, sort_order)
SELECT @stats_menu_id, '重新激活', 'btn:reactivate', 3
FROM sys_menu WHERE path = '/admin?tab=statistics' LIMIT 1
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- =============================================
-- 初始化超级管理员菜单权限（拥有所有菜单）
-- =============================================
INSERT INTO sys_role_menu (role_id, role_code, menu_id)
SELECT r.id, r.code, m.id
FROM sys_role r, sys_menu m
WHERE r.code = 'SUPER_ADMIN';

-- 初始化超级管理员按钮权限
INSERT INTO sys_role_button (role_id, role_code, button_id)
SELECT r.id, r.code, b.id
FROM sys_role r, sys_button b
WHERE r.code = 'SUPER_ADMIN';

-- =============================================
-- 初始化区域管理员菜单权限
-- =============================================
INSERT INTO sys_role_menu (role_id, role_code, menu_id)
SELECT r.id, r.code, m.id
FROM sys_role r, sys_menu m
WHERE r.code = 'REGION_ADMIN';

-- 区域管理员按钮权限（不含用户管理下的敏感按钮，暂全部授权）
INSERT INTO sys_role_button (role_id, role_code, button_id)
SELECT r.id, r.code, b.id
FROM sys_role r, sys_button b
WHERE r.code = 'REGION_ADMIN';

-- =============================================
-- 初始化窗口操作员菜单权限（只有窗口工作台）
-- =============================================
INSERT INTO sys_role_menu (role_id, role_code, menu_id)
SELECT r.id, r.code, m.id
FROM sys_role r, sys_menu m
WHERE r.code = 'WINDOW_OPERATOR' AND m.path = '/counter';

INSERT INTO sys_role_button (role_id, role_code, button_id)
SELECT r.id, r.code, b.id
FROM sys_role r, sys_button b, sys_menu m
WHERE r.code = 'WINDOW_OPERATOR' AND b.menu_id = m.id AND m.path = '/counter';

-- =============================================
-- 初始化区域数据
-- =============================================
INSERT INTO region (region_name, level, parent_id, region_code, sort_order, announcement_text) VALUES
('深圳市', 'city', NULL, '440300', 1, NULL),
('南山区', 'town', 1, '440305', 1, '本大厅周一至周五 09:00-17:00 对外服务，周末及法定节假日休息。'),
('宝安区', 'town', 1, '440306', 2, '本大厅周一至周五 09:00-17:00 对外服务，周末及法定节假日休息。'),
('东莞市', 'city', NULL, '441900', 2, NULL),
('南城街道', 'town', 4, '441901', 1, '本大厅周一至周五 09:00-17:00 对外服务。'),
('东城街道', 'town', 4, '441902', 2, '本大厅周一至周五 09:00-17:00 对外服务。');

-- =============================================
-- 初始化全局业务类型数据
-- =============================================
INSERT INTO business_type (name, prefix, description, daily_appointment_limit, is_enabled, sort_order) VALUES
('个人业务', 'A', '个人账户相关业务', 50, 1, 1),
('对公业务', 'B', '企业客户相关业务', 30, 1, 2),
('信用卡业务', 'C', '信用卡申请、还款等', 40, 1, 3),
('贷款业务', 'D', '个人及企业贷款', 20, 1, 4),
('咨询业务', 'E', '业务咨询服务', 60, 1, 5),
('VIP业务', 'F', 'VIP客户服务', 10, 1, 6);

-- =============================================
-- 初始化区域-业务关联
-- =============================================
INSERT INTO region_business (region_id, business_type_id, is_enabled, sort_order) VALUES
(2, 1, 1, 1), (2, 2, 1, 2), (2, 3, 1, 3),
(3, 1, 1, 1), (3, 4, 1, 2), (3, 6, 1, 3),
(5, 1, 1, 1), (5, 2, 1, 2), (5, 3, 1, 3),
(6, 1, 1, 1), (6, 5, 1, 2), (6, 6, 1, 3);

-- =============================================
-- 初始化服务窗口数据
-- =============================================
INSERT INTO counter (region_id, number, name, operator_name, status) VALUES
(2, 1, '南山1号窗口', '张三', 'idle'),
(2, 2, '南山2号窗口', '李四', 'idle'),
(2, 3, '南山3号窗口', '王五', 'idle'),
(3, 1, '宝安1号窗口', '赵六', 'idle'),
(3, 2, '宝安2号窗口', '钱七', 'idle'),
(3, 3, '宝安3号窗口', '孙八', 'idle'),
(5, 1, '南城1号窗口', '周九', 'idle'),
(5, 2, '南城2号窗口', '吴十', 'idle'),
(6, 1, '东城1号窗口', '郑一', 'idle'),
(6, 2, '东城2号窗口', '王二', 'idle');

-- =============================================
-- 初始化窗口与业务类型关联
-- =============================================
INSERT INTO counter_business (counter_id, business_type_id) VALUES
(1,1),(1,2),(1,3), (2,1),(2,2), (3,3),
(4,1),(4,4),(4,6), (5,1),(5,4), (6,6),
(7,1),(7,2),(7,3), (8,1),(8,2),
(9,1),(9,5),(9,6), (10,1),(10,5);
