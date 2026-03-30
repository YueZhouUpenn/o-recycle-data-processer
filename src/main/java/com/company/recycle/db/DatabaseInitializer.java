package com.company.recycle.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * 数据库初始化器
 */
public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    /**
     * 初始化数据库（建表、建索引）
     */
    public static void initialize() {
        logger.info("Initializing database schema...");

        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (Statement stmt = conn.createStatement()) {

                String schemaSql = loadSchemaFromResource();

                String[] statements = schemaSql.split(";");
                for (String sql : statements) {
                    String cleanedSql = removeComments(sql).trim();
                    if (!cleanedSql.isEmpty()) {
                        logger.debug("Executing SQL: {}", cleanedSql.substring(0, Math.min(50, cleanedSql.length())));
                        stmt.execute(cleanedSql);
                    }
                }

                ensureWideLedger(conn);

                logger.info("Database schema initialized successfully");
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize database schema", e);
            throw new RuntimeException("Database initialization failed", e);
        } finally {
            ConnectionManager.returnConnection(conn);
        }
    }

    /**
     * 旧库可能为精简版 t_ledger；与 LedgerSchema 宽表不一致时 DROP 并重建（物化数据由刷新重算）。
     */
    private static void ensureWideLedger(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type='table' AND name='t_ledger'")) {
            if (!rs.next()) {
                return;
            }
        }
        if (LedgerSchema.isWideLedger(conn)) {
            return;
        }
        logger.info("检测到旧版 t_ledger，迁移为宽表结构…");
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS t_ledger");
        }
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(LedgerSchema.CREATE_LEDGER_WIDE.trim());
        }
    }

    private static String removeComments(String sql) {
        StringBuilder result = new StringBuilder();
        String[] lines = sql.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                result.append(line).append("\n");
            }
        }
        return result.toString();
    }

    private static String loadSchemaFromResource() {
        try (InputStream is = DatabaseInitializer.class.getClassLoader()
                .getResourceAsStream("schema.sql")) {

            if (is == null) {
                throw new RuntimeException("schema.sql not found in resources");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }

        } catch (IOException e) {
            logger.error("Failed to load schema.sql", e);
            throw new RuntimeException("Failed to load database schema", e);
        }
    }

    /**
     * 检查数据库是否已初始化
     */
    public static boolean isInitialized() {
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1 FROM t_schema_version WHERE version = 1 LIMIT 1")) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        } finally {
            ConnectionManager.returnConnection(conn);
        }
    }
}
