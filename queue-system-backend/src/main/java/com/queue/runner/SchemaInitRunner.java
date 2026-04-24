package com.queue.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Collectors;

@Component
public class SchemaInitRunner implements CommandLineRunner {

    private final DataSource dataSource;

    public SchemaInitRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // 检查表是否已存在
            try (var rs = conn.getMetaData().getTables(null, null, "sys_user_menu", null)) {
                if (rs.next()) {
                    System.out.println("sys_user_menu 表已存在，跳过初始化");
                } else {
                    System.out.println("开始初始化权限表...");
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            new ClassPathResource("db/schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
                        String sql = reader.lines().collect(Collectors.joining("\n"));
                        // 移除 DROP TABLE 语句（只创建不删除）
                        sql = sql.replaceAll("(?i)DROP TABLE IF EXISTS `[^`]+`;\\s*", "");
                        // 分割执行多条语句
                        String[] statements = sql.split(";");
                        try (Statement stmt = conn.createStatement()) {
                            for (String s : statements) {
                                s = s.trim();
                                if (!s.isEmpty()) {
                                    try {
                                        stmt.execute(s);
                                    } catch (Exception e) {
                                        // 忽略已存在的表/数据错误
                                        String msg = e.getMessage();
                                        if (msg != null && (msg.contains("already exists") || msg.contains("Duplicate") || msg.contains("Data truncated"))) {
                                            // skip
                                        } else {
                                            System.err.println("SQL执行异常: " + e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    System.out.println("权限表初始化完成");
                }
            }

            // 迁移：检查并添加 skip_type 字段（如果不存在）
            migrateSkipTypeColumn(conn);

            // 迁移：添加重新激活按钮权限
            migrateReactivateButton(conn);
        }
    }

    /**
     * 迁移：为 ticket 表添加 skip_type 字段
     * 避免现有数据库没有这个字段导致报错
     */
    private void migrateSkipTypeColumn(Connection conn) throws Exception {
        try {
            // 检查 skip_type 列是否存在
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "ticket", "skip_type")) {
                columnExists = columns.next();
            }

            if (!columnExists) {
                System.out.println("检测到 ticket 表缺少 skip_type 字段，开始迁移...");
                try (Statement stmt = conn.createStatement()) {
                    // 添加 skip_type 列
                    stmt.execute("ALTER TABLE ticket ADD COLUMN skip_type VARCHAR(20) COMMENT '过号来源类型：manual=人工跳过，system=系统过号'");
                    System.out.println("ticket.skip_type 字段添加成功");
                }
            } else {
                System.out.println("ticket.skip_type 字段已存在，跳过迁移");
            }
        } catch (Exception e) {
            System.err.println("skip_type 字段迁移失败: " + e.getMessage());
            // 不阻断启动，只是记录错误
        }
    }

    /**
     * 迁移：添加"重新激活"按钮到统计分析菜单
     */
    private void migrateReactivateButton(Connection conn) throws Exception {
        try {
            // 查找统计分析菜单ID
            Long statsMenuId = null;
            try (var rs = conn.createStatement().executeQuery(
                    "SELECT id FROM sys_menu WHERE path = '/admin?tab=statistics' LIMIT 1")) {
                if (rs.next()) {
                    statsMenuId = rs.getLong(1);
                }
            }

            if (statsMenuId == null) {
                System.out.println("未找到统计分析菜单，跳过重新激活按钮迁移");
                return;
            }

            // 检查按钮是否已存在
            boolean buttonExists = false;
            try (var rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM sys_button WHERE menu_id = " + statsMenuId + " AND code = 'btn:reactivate'")) {
                if (rs.next()) {
                    buttonExists = rs.getInt(1) > 0;
                }
            }

            if (!buttonExists) {
                System.out.println("添加重新激活按钮...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO sys_button (menu_id, name, code, sort_order) VALUES (" +
                            statsMenuId + ", '重新激活', 'btn:reactivate', 3)");
                    System.out.println("重新激活按钮添加成功");

                    // 为超级管理员添加此按钮权限
                    stmt.execute("INSERT IGNORE INTO sys_role_button (role_id, role_code, button_id) " +
                            "SELECT r.id, r.code, b.id FROM sys_role r, sys_button b " +
                            "WHERE r.code = 'SUPER_ADMIN' AND b.code = 'btn:reactivate'");
                    System.out.println("超级管理员已获得重新激活按钮权限");
                }
            } else {
                System.out.println("重新激活按钮已存在，跳过迁移");
            }
        } catch (Exception e) {
            System.err.println("重新激活按钮迁移失败: " + e.getMessage());
        }
    }
}
