package com.company.recycle.exporter;

import com.company.recycle.db.ConnectionManager;
import com.company.recycle.web.LedgerFilter;
import com.company.recycle.util.NamingStrategy;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 总台账导出器
 */
public class LedgerExporter {
    private static final Logger logger = LoggerFactory.getLogger(LedgerExporter.class);

    /** PRD §5.4 主序列在前，其后 doc_type 等，再按列名排序追加 rc_/rt_ 全量字段 */
    private static final String[] EXPORT_PRIMARY_ORDER = {
            "serial_no", "material_code", "material_name", "spec", "outbound_date", "doc_no", "order_no",
            "salesperson", "sales_dept", "customer", "end_customer",
            "recycle_status", "recycle_source_file", "recycle_date", "actual_customer", "updated_at",
            "doc_type", "description", "outbound_batch_id"
    };

    private static final Map<String, String> HEADER_ZH = new LinkedHashMap<>();

    static {
        HEADER_ZH.put("serial_no", "序列号");
        HEADER_ZH.put("material_code", "物料编码");
        HEADER_ZH.put("material_name", "物料名称");
        HEADER_ZH.put("spec", "规格型号");
        HEADER_ZH.put("outbound_date", "出库日期");
        HEADER_ZH.put("doc_no", "单据编号");
        HEADER_ZH.put("order_no", "订单单号");
        HEADER_ZH.put("salesperson", "销售员");
        HEADER_ZH.put("sales_dept", "销售部门");
        HEADER_ZH.put("customer", "客户");
        HEADER_ZH.put("end_customer", "终端客户");
        HEADER_ZH.put("recycle_status", "回收状态");
        HEADER_ZH.put("recycle_source_file", "回收来源");
        HEADER_ZH.put("recycle_date", "回收日期");
        HEADER_ZH.put("actual_customer", "实际回收客户");
        HEADER_ZH.put("updated_at", "最后更新时间");
        HEADER_ZH.put("doc_type", "单据类型");
        HEADER_ZH.put("description", "描述");
        HEADER_ZH.put("outbound_batch_id", "出库导入批次ID");
    }

    private static List<String> exportColumnOrder(Connection conn) throws SQLException {
        List<String> all = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(t_ledger)")) {
            while (rs.next()) {
                all.add(rs.getString("name"));
            }
        }
        List<String> out = new ArrayList<>();
        for (String c : EXPORT_PRIMARY_ORDER) {
            if (all.contains(c)) {
                out.add(c);
            }
        }
        List<String> rest = new ArrayList<>(all);
        rest.removeAll(out);
        rest.sort(Comparator.naturalOrder());
        out.addAll(rest);
        return out;
    }

    private static String headerLabel(String col) {
        return HEADER_ZH.getOrDefault(col, col);
    }

    private static void bindParams(java.sql.PreparedStatement stmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    /**
     * 导出总台账（宽表全列；表头前段为 PRD §5.4 所列中文，扩展列为数据库列名）。
     * {@code filter} 为空或全空时导出全表。
     */
    public static Path exportLedger(LedgerFilter filter) throws SQLException, IOException {
        if (filter == null) {
            filter = LedgerFilter.fromRequest(null, null, null, null, null);
        }
        logger.info("开始导出总台账（按筛选条件）…");

        Path outputPath = NamingStrategy.resolveExportPath(
                Paths.get("./output"),
                "销售出库总台账_更新后.xlsx"
        );

        String orderBy = """
                ORDER BY
                    CASE recycle_status
                        WHEN '问题序列号' THEN 1
                        WHEN '未回收'    THEN 2
                        WHEN '退货回收'  THEN 3
                        WHEN '现场回收'  THEN 4
                        WHEN '统一回收'  THEN 5
                        ELSE 6
                    END,
                    outbound_date DESC
                """;

        Connection conn = null;
        try (Workbook workbook = new XSSFWorkbook()) {
            conn = ConnectionManager.getConnection();
            List<String> cols = exportColumnOrder(conn);
            String colList = String.join(", ", cols);
            StringBuilder sql = new StringBuilder("SELECT ").append(colList).append(" FROM t_ledger");
            List<Object> params = new ArrayList<>();
            filter.appendWhere(sql, params);
            sql.append(orderBy);

            try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
                bindParams(stmt, params);
                try (ResultSet rs = stmt.executeQuery()) {

                Sheet sheet = workbook.createSheet("总台账");

                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < cols.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headerLabel(cols.get(i)));
                    cell.setCellStyle(headerStyle);
                }

                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                int rowNum = 1;
                while (rs.next()) {
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 0; i < colCount; i++) {
                        Cell cell = row.createCell(i);
                        String value = rs.getString(i + 1);
                        cell.setCellValue(value != null ? value : "");
                    }
                }

                for (int i = 0; i < cols.size(); i++) {
                    sheet.autoSizeColumn(i);
                }

                try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                    workbook.write(fos);
                }
                }
            }
        } finally {
            ConnectionManager.returnConnection(conn);
        }

        logger.info("总台账导出完成: {}", outputPath);
        return outputPath;
    }
    
    /**
     * 获取统计信息
     */
    public static class Statistics {
        public int totalOutbound;
        public int recycled;
        public int unrecycled;
        public int problem;
        public double recycleRate;
        
        @Override
        public String toString() {
            return String.format(
                    "总出库=%d, 已回收=%d, 未回收=%d, 问题序列号=%d, 回收率=%.2f%%",
                    totalOutbound, recycled, unrecycled, problem, recycleRate
            );
        }
    }
    
    public static Statistics getStatistics() throws SQLException {
        Statistics stats = new Statistics();
        
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (Statement stmt = conn.createStatement()) {
                
                // 总出库数
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM t_outbound")) {
                    if (rs.next()) {
                        stats.totalOutbound = rs.getInt(1);
                    }
                }
                
                // 已回收数
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM t_ledger WHERE recycle_status IN ('现场回收', '统一回收', '退货回收')")) {
                    if (rs.next()) {
                        stats.recycled = rs.getInt(1);
                    }
                }
                
                // 未回收数
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM t_ledger WHERE recycle_status = '未回收'")) {
                    if (rs.next()) {
                        stats.unrecycled = rs.getInt(1);
                    }
                }
                
                // 问题序列号数
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM t_ledger WHERE recycle_status = '问题序列号'")) {
                    if (rs.next()) {
                        stats.problem = rs.getInt(1);
                    }
                }
                
                // 计算回收率
                if (stats.totalOutbound > 0) {
                    stats.recycleRate = (stats.recycled * 100.0) / stats.totalOutbound;
                }
            }
        } finally {
            ConnectionManager.returnConnection(conn);
        }
        
        return stats;
    }
}
