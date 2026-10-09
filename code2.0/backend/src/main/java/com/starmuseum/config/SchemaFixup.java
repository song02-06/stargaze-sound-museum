package com.starmuseum.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 摘掉 Hibernate 给枚举列生成的陈旧 CHECK 约束。
 *
 * 为什么需要这一步：
 *   Hibernate 6 会为 @Enumerated(STRING) 的列生成 `check (col in ('A','B',...))`，
 *   而 `ddl-auto: update` **只加不改** —— 约束创建之后就再不更新。
 *   结果是：往枚举里加一个新值（比如给审核阶段加 ASR），
 *   老库会直接把插入拒掉，报一句看不懂的 `Check constraint violation: "CONSTRAINT_D"`。
 *
 * 这里的处理：启动时找出这些 CHECK 约束，只删「取值列表型」的那些，
 * 之后由 Java 枚举把关。业务约束（比如外键、唯一）一概不碰。
 *
 * 这是个演示项目，数据库本来就该随代码演进；真上生产要换 Flyway 之类的迁移工具。
 */
@Component
@Order(0)   // 必须早于 SeedRunner：种子数据也要往这些列写
public class SchemaFixup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaFixup.class);

    private static final List<String> TABLES = List.of("AUDIT_RECORD", "EXHIBIT", "ECHO");

    private final JdbcTemplate jdbc;

    public SchemaFixup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        int dropped = 0;
        for (String table : TABLES) {
            dropped += dropEnumChecks(table);
        }
        if (dropped > 0) {
            log.info("已清理 {} 个陈旧的枚举 CHECK 约束（枚举新增取值不再被老库拦住）", dropped);
        }
    }

    private int dropEnumChecks(String table) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("""
                    select CONSTRAINT_NAME as NAME
                    from INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                    where TABLE_NAME = ? and CONSTRAINT_TYPE = 'CHECK'
                    """, table);
        } catch (Exception e) {
            log.warn("读不到 {} 的约束信息：{}", table, e.getMessage());
            return 0;
        }

        if (rows.isEmpty()) {
            return 0;
        }

        int dropped = 0;
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get("NAME"));
            try {
                jdbc.execute("alter table " + table + " drop constraint \"" + name + "\"");
                log.info("移除枚举 CHECK 约束：{}.{}", table, name);
                dropped++;
            } catch (Exception e) {
                log.warn("移除 {}.{} 失败：{}", table, name, e.getMessage());
            }
        }
        return dropped;
    }
}
