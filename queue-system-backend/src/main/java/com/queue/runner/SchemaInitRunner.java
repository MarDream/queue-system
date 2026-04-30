package com.queue.runner;

import com.queue.service.PhoneCryptoService;
import com.queue.util.PhoneUtil;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

@Component
public class SchemaInitRunner implements CommandLineRunner {

    private final DataSource dataSource;
    private final PhoneCryptoService phoneCryptoService;

    public SchemaInitRunner(DataSource dataSource, PhoneCryptoService phoneCryptoService) {
        this.dataSource = dataSource;
        this.phoneCryptoService = phoneCryptoService;
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

            // 迁移：添加 reactivated_at 字段
            migrateReactivatedAt(conn);

            // 迁移：补齐手机号保护字段
            migrateProtectedPhoneColumns(conn);

            // 迁移：补齐区域数据范围表
            migrateUserRegionScopeTable(conn);

            // 迁移：补齐热路径查询索引
            migratePerformanceIndexes(conn);

            // 迁移：添加智能问数菜单（仅超级管理员）
            migrateAiMenu(conn);
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

    private void migrateReactivatedAt(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            boolean columnExists = false;
            try (ResultSet columns = metaData.getColumns(null, null, "ticket", "reactivated_at")) {
                columnExists = columns.next();
            }
            if (!columnExists) {
                System.out.println("检测到 ticket 表缺少 reactivated_at 字段，开始迁移...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE ticket ADD COLUMN reactivated_at DATETIME COMMENT '重新激活时间，用于排序优先叫号'");
                    System.out.println("ticket.reactivated_at 字段添加成功");
                }
            }
        } catch (Exception e) {
            System.err.println("reactivated_at 字段迁移失败: " + e.getMessage());
        }
    }

    private void migrateAiMenu(Connection conn) {
        try {
            Long adminMenuId = null;
            try (var rs = conn.createStatement().executeQuery(
                    "SELECT id FROM sys_menu WHERE path = '/admin' LIMIT 1")) {
                if (rs.next()) {
                    adminMenuId = rs.getLong(1);
                }
            }
            if (adminMenuId == null) {
                return;
            }

            boolean exists = false;
            try (var rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM sys_menu WHERE path = '/admin?tab=ai'")) {
                if (rs.next()) {
                    exists = rs.getInt(1) > 0;
                }
            }
            if (!exists) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("INSERT INTO sys_menu (name, path, icon, sort_order, parent_id, type) VALUES " +
                            "('智能问数', '/admin?tab=ai', 'ChatDotRound', 13, " + adminMenuId + ", 'menu')");
                    stmt.execute("INSERT IGNORE INTO sys_role_menu (role_id, role_code, menu_id) " +
                            "SELECT r.id, r.code, m.id FROM sys_role r, sys_menu m " +
                            "WHERE r.code = 'SUPER_ADMIN' AND m.path = '/admin?tab=ai'");
                }
            }
        } catch (Exception e) {
            System.err.println("智能问数菜单迁移失败: " + e.getMessage());
        }
    }

    private void migrateProtectedPhoneColumns(Connection conn) {
        try {
            ensurePhoneColumns(conn, "ticket");
            ensurePhoneColumns(conn, "appointment");
            ensureIndex(conn, "ticket", "idx_ticket_phone_hash_type_created",
                    "CREATE INDEX idx_ticket_phone_hash_type_created ON ticket (phone_hash, business_type_id, created_at)");
            ensureIndex(conn, "ticket", "idx_ticket_phone_last4_created",
                    "CREATE INDEX idx_ticket_phone_last4_created ON ticket (phone_last4, created_at)");
            ensureIndex(conn, "appointment", "idx_appointment_phone_hash_date",
                    "CREATE INDEX idx_appointment_phone_hash_date ON appointment (phone_hash, appointment_date)");
            ensureIndex(conn, "appointment", "idx_appointment_phone_last4_date",
                    "CREATE INDEX idx_appointment_phone_last4_date ON appointment (phone_last4, appointment_date)");

            backfillProtectedPhones(conn, "ticket");
            backfillProtectedPhones(conn, "appointment");
        } catch (Exception e) {
            System.err.println("手机号保护字段迁移失败: " + e.getMessage());
        }
    }

    private void migrateUserRegionScopeTable(Connection conn) {
        try {
            if (tableExists(conn, "sys_user_region_scope")) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE sys_user_region_scope (
                            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                            user_id BIGINT NOT NULL COMMENT '用户ID',
                            region_id BIGINT NOT NULL COMMENT '区域ID',
                            UNIQUE KEY uk_user_region (user_id, region_id),
                            INDEX idx_user (user_id),
                            INDEX idx_region (region_id)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户区域数据范围'
                        """);
                System.out.println("sys_user_region_scope 表添加成功");
            }
        } catch (Exception e) {
            System.err.println("sys_user_region_scope 表迁移失败: " + e.getMessage());
        }
    }

    private void migratePerformanceIndexes(Connection conn) {
        try {
            ensureIndex(conn, "ticket", "idx_ticket_region_status_created",
                    "CREATE INDEX idx_ticket_region_status_created ON ticket (region_id, status, created_at)");
            ensureIndex(conn, "ticket", "idx_ticket_region_business_created",
                    "CREATE INDEX idx_ticket_region_business_created ON ticket (region_id, business_type_id, created_at)");
            ensureIndex(conn, "ticket", "idx_ticket_counter_created",
                    "CREATE INDEX idx_ticket_counter_created ON ticket (counter_id, created_at)");
            ensureIndex(conn, "ticket", "idx_ticket_status_created_counter",
                    "CREATE INDEX idx_ticket_status_created_counter ON ticket (status, created_at, counter_id)");
        } catch (Exception e) {
            System.err.println("热路径索引迁移失败: " + e.getMessage());
        }
    }

    private void ensurePhoneColumns(Connection conn, String tableName) throws SQLException {
        ensureColumn(conn, tableName, "phone_ciphertext",
                "ALTER TABLE " + tableName + " ADD COLUMN phone_ciphertext VARCHAR(512) COMMENT '手机号密文（AES-GCM）'");
        ensureColumn(conn, tableName, "phone_hash",
                "ALTER TABLE " + tableName + " ADD COLUMN phone_hash CHAR(64) COMMENT '手机号哈希（SHA-256）'");
        ensureColumn(conn, tableName, "phone_masked",
                "ALTER TABLE " + tableName + " ADD COLUMN phone_masked VARCHAR(20) COMMENT '手机号脱敏值'");
        ensureColumn(conn, tableName, "phone_last4",
                "ALTER TABLE " + tableName + " ADD COLUMN phone_last4 VARCHAR(4) COMMENT '手机号后4位'");
        ensureColumn(conn, tableName, "phone_key_version",
                "ALTER TABLE " + tableName + " ADD COLUMN phone_key_version INT DEFAULT 1 COMMENT '手机号加密密钥版本'");
    }

    private void ensureColumn(Connection conn, String tableName, String columnName, String alterSql) throws SQLException {
        if (columnExists(conn, tableName, columnName)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
            System.out.println(tableName + "." + columnName + " 字段添加成功");
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet tables = metaData.getTables(null, null, tableName, null)) {
            return tables.next();
        }
    }

    private void ensureIndex(Connection conn, String tableName, String indexName, String createSql) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (indexes.next()) {
                String existingIndex = indexes.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(existingIndex)) {
                    return;
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
            System.out.println(tableName + "." + indexName + " 索引添加成功");
        }
    }

    private void backfillProtectedPhones(Connection conn, String tableName) throws SQLException {
        String selectSql = """
                SELECT id, phone, phone_ciphertext, phone_hash, phone_masked, phone_last4, phone_key_version
                FROM %s
                WHERE phone IS NOT NULL
                  AND (
                      phone_ciphertext IS NULL
                      OR phone_hash IS NULL
                      OR phone_masked IS NULL
                      OR phone_last4 IS NULL
                      OR phone_key_version IS NULL
                  )
                """.formatted(tableName);
        String updateSql = """
                UPDATE %s
                SET phone = ?,
                    phone_ciphertext = ?,
                    phone_hash = ?,
                    phone_masked = ?,
                    phone_last4 = ?,
                    phone_key_version = ?
                WHERE id = ?
                """.formatted(tableName);

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             ResultSet resultSet = selectStmt.executeQuery();
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            int migrated = 0;
            while (resultSet.next()) {
                Long id = resultSet.getLong("id");
                String storedPhone = resultSet.getString("phone");
                String phoneCiphertext = resultSet.getString("phone_ciphertext");
                String phoneHash = resultSet.getString("phone_hash");
                String phoneMasked = resultSet.getString("phone_masked");
                String phoneLast4 = resultSet.getString("phone_last4");
                Integer phoneKeyVersion = resultSet.getObject("phone_key_version", Integer.class);

                if (storedPhone == null || storedPhone.isBlank()) {
                    continue;
                }

                boolean looksMasked = storedPhone.contains("*");
                PhoneCryptoService.ProtectedPhone protectedPhone = looksMasked ? null : phoneCryptoService.protect(storedPhone);

                String nextMasked = firstNonBlank(phoneMasked,
                        protectedPhone != null ? protectedPhone.masked() : PhoneUtil.mask(storedPhone));
                String nextLast4 = firstNonBlank(phoneLast4,
                        protectedPhone != null ? protectedPhone.last4() : PhoneUtil.extractLast4(storedPhone));
                String nextCiphertext = firstNonBlank(phoneCiphertext,
                        protectedPhone != null ? protectedPhone.ciphertext() : null);
                String nextHash = firstNonBlank(phoneHash,
                        protectedPhone != null ? protectedPhone.hash() : null);
                Integer nextKeyVersion = phoneKeyVersion != null
                        ? phoneKeyVersion
                        : protectedPhone != null ? protectedPhone.keyVersion() : phoneCryptoService.keyVersionValue();
                String nextStoredPhone = firstNonBlank(nextMasked, storedPhone);

                updateStmt.setString(1, nextStoredPhone);
                updateStmt.setString(2, nextCiphertext);
                updateStmt.setString(3, nextHash);
                updateStmt.setString(4, nextMasked);
                updateStmt.setString(5, nextLast4);
                updateStmt.setInt(6, nextKeyVersion);
                updateStmt.setLong(7, id);
                migrated += updateStmt.executeUpdate();
            }

            if (migrated > 0) {
                System.out.println(tableName + " 手机号保护字段回填完成，共更新 " + migrated + " 条");
            }
        }
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
