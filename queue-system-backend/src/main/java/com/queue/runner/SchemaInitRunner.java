package com.queue.runner;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
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
                    return;
                }
            }

            System.out.println("开始初始化权限表...");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new ClassPathResource("db/schema_permission.sql").getInputStream(), StandardCharsets.UTF_8))) {
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
}
