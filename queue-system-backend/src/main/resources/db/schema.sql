-- 排队叫号系统数据库建表脚本
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
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_region_code` (`region_code`),
    INDEX `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行政区域表';

-- 业务类型表（全局定义，不绑定区域）
CREATE TABLE IF NOT EXISTS `business_type` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `name` VARCHAR(50) NOT NULL COMMENT '业务类型名称',
    `prefix` CHAR(1) NOT NULL COMMENT '票号前缀代码',
    `description` VARCHAR(200) COMMENT '业务类型描述',
    `daily_appointment_limit` INT DEFAULT 50 COMMENT '每日预约限额',
    `is_enabled` TINYINT(1) DEFAULT 1 COMMENT '全局是否启用：1=是，0=否',
    `sort_order` INT DEFAULT 0 COMMENT '显示排序顺序',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_region_number` (`region_id`, `number`),
    INDEX `idx_region` (`region_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务窗口表';

-- 窗口与业务类型关联表
CREATE TABLE IF NOT EXISTS `counter_business` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `counter_id` BIGINT NOT NULL COMMENT '窗口ID',
    `business_type_id` BIGINT NOT NULL COMMENT '业务类型ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_counter_business` (`counter_id`, `business_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='窗口与业务类型关联表';

-- 排队票号表
CREATE TABLE IF NOT EXISTS `ticket` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `region_id` BIGINT NOT NULL COMMENT '所属区域ID',
    `ticket_no` VARCHAR(10) NOT NULL COMMENT '票号',
    `business_type_id` BIGINT NOT NULL COMMENT '业务类型ID',
    `source` VARCHAR(20) DEFAULT 'online' COMMENT '来源：online=扫码取号，appointment=预约，manual=管理员代录',
    `phone` VARCHAR(20) COMMENT '手机号码（脱敏后）',
    `name` VARCHAR(50) COMMENT '客户姓名',
    `status` VARCHAR(20) DEFAULT 'waiting' COMMENT '状态：waiting=等待中，called=已叫号，serving=服务中，completed=已完成，skipped=已跳过，cancelled=已取消',
    `counter_id` BIGINT COMMENT '分配的窗口ID',
    `called_at` DATETIME COMMENT '被叫时间',
    `served_at` DATETIME COMMENT '开始服务时间',
    `completed_at` DATETIME COMMENT '服务完成时间',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `config_key` VARCHAR(50) NOT NULL COMMENT '配置项键名',
    `config_value` VARCHAR(200) COMMENT '配置项键值',
    `description` VARCHAR(200) COMMENT '配置项描述',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='窗口与操作员关联表';

-- 二维码记录表
CREATE TABLE IF NOT EXISTS `qrcode_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `region_id` BIGINT NOT NULL COMMENT '区域ID',
    `region_code` VARCHAR(20) NOT NULL COMMENT '区域代码（冗余，方便查询）',
    `region_name` VARCHAR(50) NOT NULL COMMENT '区域名称',
    `url` VARCHAR(500) NOT NULL COMMENT '二维码URL',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_region` (`region_id`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='二维码记录表';

-- 系统用户表
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码（BCrypt加密）',
    `name` VARCHAR(50) COMMENT '真实姓名',
    `role` VARCHAR(20) NOT NULL COMMENT '角色：SUPER_ADMIN=超级管理员，REGION_ADMIN=区域管理员，WINDOW_OPERATOR=窗口操作员',
    `region_id` BIGINT COMMENT '管辖区域ID（区域管理员必填）',
    `status` TINYINT(1) DEFAULT 1 COMMENT '状态：1=启用，0=禁用',
    `last_login_at` DATETIME COMMENT '最后登录时间',
    `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted` TINYINT(1) DEFAULT 0 COMMENT '软删除标记：1=已删除，0=正常',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_username` (`username`),
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
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
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
    `updated_at` DATETIME ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_menu_code` (`menu_id`, `code`),
    INDEX `idx_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='按钮权限表';


-- 角色菜单关联表
CREATE TABLE `sys_role_menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `role` VARCHAR(20) NOT NULL COMMENT '角色',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    UNIQUE KEY `uk_role_menu` (`role`, `menu_id`),
    INDEX `idx_role` (`role`),
    INDEX `idx_menu` (`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';

-- 角色按钮关联表
CREATE TABLE `sys_role_button` (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `role` VARCHAR(20) NOT NULL COMMENT '角色',
    `button_id` BIGINT NOT NULL COMMENT '按钮ID',
    UNIQUE KEY `uk_role_button` (`role`, `button_id`),
    INDEX `idx_role` (`role`),
    INDEX `idx_button` (`button_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色按钮关联表';

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
-- 初始化数据
-- =============================================
-- 初始化超级管理员（密码：admin123，BCrypt加密）
INSERT INTO `sys_user` (`username`, `password`, `name`, `role`, `status`) VALUES
('admin', '$2a$10$YvDcCi7FNwYahz2JkUwYqOcugV1mocqOUb2EZvWEsihhcercZ4bUG', '超级管理员', 'SUPER_ADMIN', 1);

-- 初始化菜单数据
INSERT INTO `sys_menu` (`name`, `path`, `icon`, `sort_order`, `type`) VALUES
('系统配置', '/admin', '📊', 1, 'page'),
('区域管理', '/admin?tab=region', '🗺️', 2, 'page'),
('业务类型', '/admin?tab=biz', '📋', 3, 'page'),
('窗口管理', '/admin?tab=counters', '🪟', 4, 'page'),
('二维码生成', '/admin?tab=qrcode', '📱', 5, 'page'),
('用户管理', '/admin?tab=users', '👤', 6, 'page'),
('菜单管理', '/admin?tab=menu', '📝', 7, 'page'),
('窗口工作台', '/counter', '🎯', 10, 'page'),
('叫号大屏', '/display', '📺', 11, 'page');

-- 初始化按钮权限
INSERT INTO `sys_button` (`menu_id`, `name`, `code`, `sort_order`) VALUES
-- 数据概览页面按钮
(1, '刷新', 'btn:refresh', 1),
-- 区域管理按钮
(2, '新增', 'btn:add', 1),
(2, '编辑', 'btn:edit', 2),
(2, '删除', 'btn:delete', 3),
(2, '保存排序', 'btn:saveSort', 4),
-- 业务类型按钮
(3, '新增', 'btn:add', 1),
(3, '编辑', 'btn:edit', 2),
(3, '删除', 'btn:delete', 3),
(3, '保存排序', 'btn:saveSort', 4),
-- 窗口管理按钮
(4, '新增', 'btn:add', 1),
(4, '编辑', 'btn:edit', 2),
(4, '删除', 'btn:delete', 3),
(4, '启用/禁用', 'btn:toggle', 4),
-- 二维码生成按钮
(5, '生成', 'btn:generate', 1),
(5, '下载', 'btn:download', 2),
(5, '删除', 'btn:delete', 3),
-- 用户管理按钮
(6, '新增', 'btn:add', 1),
(6, '编辑', 'btn:edit', 2),
(6, '删除', 'btn:delete', 3),
(6, '重置密码', 'btn:resetPwd', 4),
(6, '保存排序', 'btn:saveSort', 5);

-- 初始化超级管理员的菜单权限（拥有所有菜单）
INSERT INTO `sys_role_menu` (`role`, `menu_id`)
SELECT 'SUPER_ADMIN', `id` FROM `sys_menu`;

-- 初始化超级管理员的按钮权限（拥有所有按钮）
INSERT INTO `sys_role_button` (`role`, `button_id`)
SELECT 'SUPER_ADMIN', `id` FROM `sys_button`;

-- 初始化区域管理员的菜单权限（管理后台所有菜单，包含用户管理）
INSERT INTO `sys_role_menu` (`role`, `menu_id`)
SELECT 'REGION_ADMIN', `id` FROM `sys_menu`;

-- 初始化区域管理员的按钮权限（用户管理中排除权限配置按钮）
INSERT INTO `sys_role_button` (`role`, `button_id`)
SELECT 'REGION_ADMIN', `id` FROM `sys_button` WHERE `menu_id` IN (
    SELECT `id` FROM `sys_menu` WHERE `path` != '/admin?tab=users'
);
-- 区域管理员：用户管理按钮权限（新增、编辑、重置密码、删除，不含权限配置）
INSERT INTO `sys_role_button` (`role`, `button_id`)
SELECT 'REGION_ADMIN', `id` FROM `sys_button`
WHERE `menu_id` = (SELECT `id` FROM `sys_menu` WHERE `path` = '/admin?tab=users')
  AND `code` IN ('btn:add', 'btn:edit', 'btn:resetPwd', 'btn:delete');

-- 初始化窗口操作员的菜单权限（只有窗口工作台）
INSERT INTO `sys_role_menu` (`role`, `menu_id`)
SELECT 'WINDOW_OPERATOR', `id` FROM `sys_menu` WHERE `path` = '/counter';

-- 初始化窗口操作员的按钮权限（窗口工作台的操作按钮）
INSERT INTO `sys_role_button` (`role`, `button_id`)
SELECT 'WINDOW_OPERATOR', `id` FROM `sys_button` WHERE `menu_id` = (
    SELECT `id` FROM `sys_menu` WHERE `path` = '/counter'
);

-- 初始化区域数据（深圳市 + 东莞市 + 子区域）
INSERT INTO region (region_name, level, parent_id, region_code, sort_order, announcement_text) VALUES
('深圳市', 'city', NULL, '440300', 1, NULL),
('南山区', 'town', 1, '440305', 1, '本大厅周一至周五 09:00-17:00 对外服务，周末及法定节假日休息。如有疑问请拨打服务热线 400-888-9999。'),
('宝安区', 'town', 1, '440306', 2, '本大厅周一至周五 09:00-17:00 对外服务，周末及法定节假日休息。如有疑问请拨打服务热线 400-888-8888。'),
('东莞市', 'city', NULL, '441900', 2, NULL),
('南城街道', 'town', 4, '441901', 1, '本大厅周一至周五 09:00-17:00 对外服务，周末及法定节假日休息。如有疑问请拨打服务热线 400-777-9999。'),
('东城街道', 'town', 4, '441902', 2, '本大厅周一至周五 09:00-17:00 对外服务，周末及法定节假日休息。如有疑问请拨打服务热线 400-777-8888。');

-- 初始化全局业务类型数据（不绑定区域）
INSERT INTO business_type (name, prefix, description, daily_appointment_limit, is_enabled, sort_order) VALUES
('个人业务', 'A', '个人账户相关业务', 50, 1, 1),
('对公业务', 'B', '企业客户相关业务', 30, 1, 2),
('信用卡业务', 'C', '信用卡申请、还款等', 40, 1, 3),
('贷款业务', 'D', '个人及企业贷款', 20, 1, 4),
('咨询业务', 'E', '业务咨询服务', 60, 1, 5),
('VIP业务', 'F', 'VIP客户服务', 10, 1, 6);

-- 初始化区域-业务关联（南山区：个人、对公、信用卡）region_id=2
INSERT INTO region_business (region_id, business_type_id, is_enabled, sort_order) VALUES
(2, 1, 1, 1),  -- 南山区-个人业务
(2, 2, 1, 2),  -- 南山区-对公业务
(2, 3, 1, 3);  -- 南山区-信用卡业务

-- 初始化区域-业务关联（宝安区：个人、贷款、VIP）region_id=3
INSERT INTO region_business (region_id, business_type_id, is_enabled, sort_order) VALUES
(3, 1, 1, 1),  -- 宝安区-个人业务
(3, 4, 1, 2),  -- 宝安区-贷款业务
(3, 6, 1, 3);  -- 宝安区-VIP业务

-- 初始化区域-业务关联（南城街道：个人、对公、信用卡）
INSERT INTO region_business (region_id, business_type_id, is_enabled, sort_order) VALUES
(5, 1, 1, 1),  -- 南城街道-个人业务
(5, 2, 1, 2),  -- 南城街道-对公业务
(5, 3, 1, 3);  -- 南城街道-信用卡业务

-- 初始化区域-业务关联（东城街道：个人、咨询、VIP）
INSERT INTO region_business (region_id, business_type_id, is_enabled, sort_order) VALUES
(6, 1, 1, 1),  -- 东城街道-个人业务
(6, 5, 1, 2),  -- 东城街道-咨询业务
(6, 6, 1, 3);  -- 东城街道-VIP业务

-- 初始化服务窗口数据（南山区，region_id=2）
INSERT INTO counter (region_id, number, name, operator_name, status) VALUES
(2, 1, '南山1号窗口', '张三', 'idle'),
(2, 2, '南山2号窗口', '李四', 'idle'),
(2, 3, '南山3号窗口', '王五', 'idle');

-- 初始化服务窗口数据（宝安区，region_id=3）
INSERT INTO counter (region_id, number, name, operator_name, status) VALUES
(3, 1, '宝安1号窗口', '赵六', 'idle'),
(3, 2, '宝安2号窗口', '钱七', 'idle'),
(3, 3, '宝安3号窗口', '孙八', 'idle');

-- 初始化服务窗口数据（南城街道，region_id=5）
INSERT INTO counter (region_id, number, name, operator_name, status) VALUES
(5, 1, '南城1号窗口', '周九', 'idle'),
(5, 2, '南城2号窗口', '吴十', 'idle');

-- 初始化服务窗口数据（东城街道，region_id=6）
INSERT INTO counter (region_id, number, name, operator_name, status) VALUES
(6, 1, '东城1号窗口', '郑一', 'idle'),
(6, 2, '东城2号窗口', '王二', 'idle');

-- 初始化窗口与业务类型关联数据（南山区：柜台1-3支持业务类型1,2,3）
INSERT INTO counter_business (counter_id, business_type_id) VALUES
(1,1),(1,2),(1,3),
(2,1),(2,2),
(3,3);

-- 初始化窗口与业务类型关联数据（宝安区：柜台4-6支持业务类型1,4,6）
INSERT INTO counter_business (counter_id, business_type_id) VALUES
(4,1),(4,4),(4,6),
(5,1),(5,4),
(6,6);

-- 初始化窗口与业务类型关联数据（南城街道：柜台7-8支持业务类型1,2,3）
INSERT INTO counter_business (counter_id, business_type_id) VALUES
(7,1),(7,2),(7,3),
(8,1),(8,2);

-- 初始化窗口与业务类型关联数据（东城街道：柜台9-10支持业务类型1,5,6）
INSERT INTO counter_business (counter_id, business_type_id) VALUES
(9,1),(9,5),(9,6),
(10,1),(10,5);

-- 初始化系统菜单数据
INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type) VALUES
('首页', '/home', 'HomeFilled', 1, NULL, 'menu'),
('取号', '/kiosk', 'Tickets', 2, NULL, 'menu'),
('大屏', '/display', 'Monitor', 3, NULL, 'menu'),
('窗口', '/counter', 'OfficeBuilding', 4, NULL, 'menu'),
('管理', '/admin', 'Setting', 5, NULL, 'menu');

-- 初始化系统按钮数据
INSERT INTO sys_button (menu_id, name, code, sort_order) VALUES
(5, '叫号', 'btn_call', 1),
(5, '完成', 'btn_complete', 2),
(5, '暂停', 'btn_pause', 3),
(5, '跳过', 'btn_skip', 4),
(5, '重新叫号', 'btn_recall', 5);

-- 初始化超级管理员权限
INSERT INTO sys_role_menu (role, menu_id) VALUES
('SUPER_ADMIN', 1), ('SUPER_ADMIN', 2), ('SUPER_ADMIN', 3), ('SUPER_ADMIN', 4), ('SUPER_ADMIN', 5);

-- 初始化超级管理员按钮权限
INSERT INTO sys_role_button (role, button_id) VALUES
('SUPER_ADMIN', 1), ('SUPER_ADMIN', 2), ('SUPER_ADMIN', 3), ('SUPER_ADMIN', 4), ('SUPER_ADMIN', 5);

-- 初始化区域管理员权限（默认：首页、取号、大屏、窗口，不含管理菜单）
INSERT INTO sys_role_menu (role, menu_id) VALUES
('REGION_ADMIN', 1), ('REGION_ADMIN', 2), ('REGION_ADMIN', 3), ('REGION_ADMIN', 4);

INSERT INTO sys_role_button (role, button_id) VALUES
('REGION_ADMIN', 1), ('REGION_ADMIN', 2), ('REGION_ADMIN', 3), ('REGION_ADMIN', 4), ('REGION_ADMIN', 5);
