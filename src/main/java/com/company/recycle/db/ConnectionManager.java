package com.company.recycle.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 数据库连接池管理器（简单实现）
 */
public class ConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionManager.class);
    private static final String DB_PATH = "./data/recycle.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;
    private static final int POOL_SIZE = 5;
    
    private static BlockingQueue<Connection> pool;
    private static boolean initialized = false;
    
    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            logger.error("SQLite JDBC driver not found", e);
        }
    }
    
    /**
     * 初始化连接池
     */
    public static synchronized void initialize() throws SQLException {
        if (initialized) {
            return;
        }
        
        pool = new ArrayBlockingQueue<>(POOL_SIZE);
        for (int i = 0; i < POOL_SIZE; i++) {
            Connection conn = DriverManager.getConnection(DB_URL);
            conn.setAutoCommit(true);
            pool.offer(conn);
        }
        
        initialized = true;
        logger.info("Database connection pool initialized with {} connections", POOL_SIZE);
    }
    
    /**
     * 获取连接
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized) {
            initialize();
        }
        
        try {
            Connection conn = pool.take();
            if (conn.isClosed()) {
                conn = DriverManager.getConnection(DB_URL);
                conn.setAutoCommit(true);
            }
            return conn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Failed to get connection from pool", e);
        }
    }
    
    /**
     * 归还连接
     */
    public static void returnConnection(Connection conn) {
        if (conn != null && pool != null) {
            try {
                if (!conn.isClosed()) {
                    pool.offer(conn);
                }
            } catch (SQLException e) {
                logger.error("Error returning connection to pool", e);
            }
        }
    }
    
    /**
     * 是否在运行中因删除/替换库文件导致连接失效（SQLite 典型：READONLY_DBMOVED）。
     */
    public static boolean isSqliteStaleFileError(SQLException e) {
        if (e == null) {
            return false;
        }
        String m = e.getMessage();
        if (m == null) {
            return false;
        }
        return m.contains("READONLY_DBMOVED")
                || m.contains("SQLITE_READONLY")
                || m.contains("attempt to write a readonly database");
    }

    /**
     * 关闭旧连接池、重新建池并执行建表脚本（用于手工替换 recycle.db 后恢复写入）。
     */
    public static synchronized void reconnectAfterExternalDbChange() throws SQLException {
        logger.warn("Resetting JDBC pool and re-applying schema (database file may have been replaced)");
        shutdown();
        initialize();
        try {
            DatabaseInitializer.initialize();
        } catch (RuntimeException ex) {
            Throwable c = ex.getCause();
            if (c instanceof SQLException) {
                throw (SQLException) c;
            }
            throw new SQLException(ex.getMessage(), ex);
        }
    }

    /**
     * 关闭连接池
     */
    public static synchronized void shutdown() {
        if (pool != null) {
            for (Connection conn : pool) {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
            pool.clear();
        }
        initialized = false;
        logger.info("Database connection pool shutdown");
    }
}
