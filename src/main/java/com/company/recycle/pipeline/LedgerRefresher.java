package com.company.recycle.pipeline;

import com.company.recycle.db.ConnectionManager;
import com.company.recycle.util.SqlResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 全量刷新 t_ledger，并在同一流程内重写刷新类 t_anomaly（多来源冲突、回收越界）。
 */
public class LedgerRefresher {
    private static final Logger logger = LoggerFactory.getLogger(LedgerRefresher.class);

    public static void refreshLedger() throws SQLException {
        SQLException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                refreshLedgerOnce();
                return;
            } catch (SQLException e) {
                last = e;
                if (attempt == 0 && ConnectionManager.isSqliteStaleFileError(e)) {
                    logger.warn("刷新时检测到数据库连接失效，重置连接池并重试一次: {}", e.getMessage());
                    ConnectionManager.reconnectAfterExternalDbChange();
                    continue;
                }
                throw e;
            }
        }
        throw new SQLException("刷新在重连后仍失败", last);
    }

    private static void refreshLedgerOnce() throws SQLException {
        logger.info("开始刷新总台账与异常表…");

        Connection conn = null;
        boolean ok = false;
        try {
            conn = ConnectionManager.getConnection();
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM t_anomaly");

                String ledgerSql = loadRefreshLedgerSql();

                int ledgerRows = stmt.executeUpdate(ledgerSql);
                logger.info("总台账写入完成，影响行数: {}", ledgerRows);

                String multiSourceSql = """
                        INSERT INTO t_anomaly (serial_no, anomaly_type, detail, source_files, batch_id, created_at)
                        SELECT
                            serial_no,
                            '多来源冲突',
                            '序列号 ' || serial_no || ' 同时出现在：' || sources,
                            sources,
                            NULL,
                            datetime('now', 'localtime')
                        FROM (
                            SELECT
                                serial_no,
                                COUNT(DISTINCT recycle_source) AS cnt,
                                GROUP_CONCAT(DISTINCT recycle_source) AS sources
                            FROM (
                                SELECT serial_no, recycle_source FROM t_recycle
                                UNION ALL
                                SELECT serial_no, '退货回收' FROM t_return
                            )
                            GROUP BY serial_no
                            HAVING cnt >= 2
                        )
                        """;

                int multi = stmt.executeUpdate(multiSourceSql);
                logger.info("多来源冲突异常写入: {} 条", multi);

                String outRecycleSql = """
                        INSERT INTO t_anomaly (serial_no, anomaly_type, detail, source_files, batch_id, created_at)
                        SELECT DISTINCT
                            r.serial_no,
                            '回收越界',
                            '序列号 ' || r.serial_no || ' 在 ' || r.recycle_source || ' 中存在，但不在销售出库单中',
                            r.recycle_source,
                            NULL,
                            datetime('now', 'localtime')
                        FROM t_recycle r
                        LEFT JOIN t_outbound o ON r.serial_no = o.serial_no
                        WHERE o.serial_no IS NULL
                        """;

                int or1 = stmt.executeUpdate(outRecycleSql);

                String outReturnSql = """
                        INSERT INTO t_anomaly (serial_no, anomaly_type, detail, source_files, batch_id, created_at)
                        SELECT DISTINCT
                            rt.serial_no,
                            '回收越界',
                            '序列号 ' || rt.serial_no || ' 在退货表中存在，但不在销售出库单中',
                            '退货表',
                            NULL,
                            datetime('now', 'localtime')
                        FROM t_return rt
                        LEFT JOIN t_outbound o ON rt.serial_no = o.serial_no
                        WHERE o.serial_no IS NULL
                        """;

                int or2 = stmt.executeUpdate(outReturnSql);
                logger.info("回收越界异常写入: {} + {} 条", or1, or2);
            }

            conn.commit();
            ok = true;
            logger.info("总台账与异常表刷新已提交");

        } catch (SQLException e) {
            if (conn != null && !ok) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.warn("刷新回滚失败", ex);
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    logger.warn("恢复 autoCommit 失败", e);
                }
                ConnectionManager.returnConnection(conn);
            }
        }
    }

    private static String loadRefreshLedgerSql() throws SQLException {
        try {
            return SqlResource.loadUtf8("sql/refresh_ledger.sql").trim();
        } catch (IOException e) {
            throw new SQLException("无法加载 sql/refresh_ledger.sql", e);
        }
    }
}

