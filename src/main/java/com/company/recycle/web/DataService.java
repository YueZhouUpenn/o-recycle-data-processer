package com.company.recycle.web;

import com.company.recycle.db.ConnectionManager;
import com.company.recycle.pipeline.FileImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * 数据查询服务
 */
public class DataService {
    private static final Logger logger = LoggerFactory.getLogger(DataService.class);

    /**
     * NULL值处理
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> ledgerRowFromResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int n = meta.getColumnCount();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= n; i++) {
            String name = meta.getColumnLabel(i);
            row.put(name, nvl(rs.getString(i)));
        }
        return row;
    }

    private static final String LEDGER_ORDER_BY = """
            ORDER BY
                CASE recycle_status
                    WHEN '问题序列号' THEN 1
                    WHEN '未回收'    THEN 2
                    WHEN '退货回收'  THEN 3
                    WHEN '现场回收'  THEN 4
                    WHEN '统一回收'  THEN 5
                    ELSE 6
                END,
                outbound_date DESC,
                serial_no
            """;

    /** 出库主表行数（PRD §5.1.1 导入前置条件） */
    public int getOutboundCount() throws SQLException {
        return FileImporter.countOutboundRows();
    }

    /**
     * 分页查询总台账（可选筛选，见 {@link LedgerFilter}）。
     */
    public List<Map<String, Object>> getLedgerData(int page, int pageSize, LedgerFilter filter) throws SQLException {
        long start = System.currentTimeMillis();
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 50;
        }
        if (filter == null) {
            filter = LedgerFilter.fromRequest(null, null, null, null, null);
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM t_ledger");
        List<Object> params = new ArrayList<>();
        filter.appendWhere(sql, params);
        sql.append(LEDGER_ORDER_BY).append(" LIMIT ? OFFSET ?");
        int offset = (page - 1) * pageSize;
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> result = new ArrayList<>();
        logger.info("DataService.getLedgerData 开始, page={}, pageSize={}, offset={}", page, pageSize, offset);

        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                bindParams(stmt, params);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(ledgerRowFromResultSet(rs));
                    }
                }
            }
        } finally {
            if (conn != null) {
                ConnectionManager.returnConnection(conn);
            }
        }
        logger.info("DataService.getLedgerData 完成, resultSize={}, costMs={}", result.size(), System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 符合条件的总台账行数。
     */
    public int getLedgerCount(LedgerFilter filter) throws SQLException {
        if (filter == null) {
            filter = LedgerFilter.fromRequest(null, null, null, null, null);
        }
        long start = System.currentTimeMillis();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM t_ledger");
        List<Object> params = new ArrayList<>();
        filter.appendWhere(sql, params);

        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        logger.info("DataService.getLedgerCount 完成, count={}, costMs={}", count, System.currentTimeMillis() - start);
                        return count;
                    }
                }
            }
        } finally {
            if (conn != null) {
                ConnectionManager.returnConnection(conn);
            }
        }
        return 0;
    }

    private static void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    /**
     * PRD §5.8 条件回收率：在筛选集合 S 上 total=|S|，unrecycled 为 {@code recycle_status='未回收'} 行数。
     */
    public Map<String, Object> getConditionalSummary(LedgerFilter filter) throws SQLException {
        if (filter == null) {
            filter = LedgerFilter.fromRequest(null, null, null, null, null);
        }
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS total,
                       COALESCE(SUM(CASE WHEN recycle_status = '未回收' THEN 1 ELSE 0 END), 0) AS unrec
                FROM t_ledger
                """);
        List<Object> params = new ArrayList<>();
        filter.appendWhere(sql, params);

        Map<String, Object> out = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int total = rs.getInt("total");
                        int unrec = rs.getInt("unrec");
                        out.put("totalRows", total);
                        out.put("unrecycledCount", unrec);
                        double rate = 0;
                        if (total > 0) {
                            rate = Math.round(10000.0 * (1.0 - (double) unrec / total)) / 100.0;
                        }
                        out.put("recycleRate", rate);
                    }
                }
            }
        } finally {
            if (conn != null) {
                ConnectionManager.returnConnection(conn);
            }
        }
        return out;
    }

    /**
     * 获取统计信息（与技术方案 §10 / PRD 看板口径对齐）
     */
    public Map<String, Object> getStatistics() throws SQLException {
        long start = System.currentTimeMillis();
        Map<String, Object> stats = new LinkedHashMap<>();
        logger.info("DataService.getStatistics 开始");

        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (Statement stmt = conn.createStatement()) {

                int totalOutbound = 0;
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_outbound")) {
                    if (rs.next()) {
                        totalOutbound = rs.getInt(1);
                    }
                }

                int recycled = 0;
                try (ResultSet rs = stmt.executeQuery("""
                        SELECT COUNT(*) FROM t_ledger
                        WHERE recycle_status IN ('现场回收', '统一回收', '退货回收')
                        """)) {
                    if (rs.next()) {
                        recycled = rs.getInt(1);
                    }
                }

                int unrecycled = 0;
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM t_ledger WHERE recycle_status = '未回收'")) {
                    if (rs.next()) {
                        unrecycled = rs.getInt(1);
                    }
                }

                double recycleRate = 0;
                try (ResultSet rs = stmt.executeQuery("""
                        SELECT
                            ROUND(
                                100.0 * SUM(CASE WHEN recycle_status IN ('现场回收','统一回收','退货回收') THEN 1 ELSE 0 END)
                                / NULLIF(COUNT(*), 0), 2
                            ) AS r
                        FROM t_ledger
                        """)) {
                    if (rs.next()) {
                        recycleRate = rs.getDouble("r");
                        if (rs.wasNull()) {
                            recycleRate = 0;
                        }
                    }
                }

                stats.put("totalOutbound", totalOutbound);
                stats.put("recycled", recycled);
                stats.put("unrecycled", unrecycled);
                stats.put("recycleRate", Math.round(recycleRate * 100.0) / 100.0);

                logger.info("DataService.getStatistics 结果: outbound={}, recycled={}, unrecycled={}, recycleRate={}",
                        totalOutbound, recycled, unrecycled, recycleRate);
            }
        } finally {
            if (conn != null) {
                ConnectionManager.returnConnection(conn);
                logger.info("DataService.getStatistics 连接已归还");
            }
        }
        logger.info("DataService.getStatistics 完成, costMs={}", System.currentTimeMillis() - start);

        return stats;
    }

    /**
     * 获取回收状态分布
     */
    public List<Map<String, Object>> getStatusDistribution() throws SQLException {
        long start = System.currentTimeMillis();
        String sql = """
            SELECT
                recycle_status,
                COUNT(*) as count
            FROM t_ledger
            GROUP BY recycle_status
            ORDER BY count DESC
            """;

        List<Map<String, Object>> result = new ArrayList<>();
        logger.info("DataService.getStatusDistribution 开始");

        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("status", nvl(rs.getString("recycle_status")));
                    row.put("count", rs.getInt("count"));
                    result.add(row);
                }
            }
        } finally {
            if (conn != null) {
                ConnectionManager.returnConnection(conn);
                logger.info("DataService.getStatusDistribution 连接已归还");
            }
        }
        logger.info("DataService.getStatusDistribution 完成, resultSize={}, costMs={}", result.size(), System.currentTimeMillis() - start);

        return result;
    }

    /**
     * {@code t_anomaly} 全列分页（按 id 倒序，与 PRD「异常信息」Tab 一致）。
     */
    public List<Map<String, Object>> getAnomalies(int page, int pageSize) throws SQLException {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 50;
        }
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM t_anomaly ORDER BY id DESC LIMIT ? OFFSET ?";
        List<Map<String, Object>> result = new ArrayList<>();
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pageSize);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(ledgerRowFromResultSet(rs));
                    }
                }
            }
        } finally {
            if (conn != null) {
                ConnectionManager.returnConnection(conn);
            }
        }
        return result;
    }

    public int getAnomalyCount() throws SQLException {
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM t_anomaly")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } finally {
            if (conn != null) {
                ConnectionManager.returnConnection(conn);
            }
        }
        return 0;
    }
}
