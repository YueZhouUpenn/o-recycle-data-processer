package com.company.recycle.pipeline;

import com.company.recycle.db.ConnectionManager;
import com.company.recycle.exception.DuplicateImportException;
import com.company.recycle.exception.ImportValidationException;
import com.company.recycle.util.DataCleaner;
import com.company.recycle.util.DateParser;
import com.company.recycle.util.ExcelFileTypeDetector;
import com.company.recycle.util.ExcelUtil;
import com.company.recycle.util.FileHashUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单文件单事务导入：任一行失败则整文件回滚，导入阶段不向 t_anomaly 写入。
 * 回收/退货导入前要求 t_outbound 已有数据（PRD §5.1.1）；库文件被替换后自动重连并重试一次。
 */
public class FileImporter {
    private static final Logger logger = LoggerFactory.getLogger(FileImporter.class);

    private static final Set<String> RECYCLE_OR_RETURN_TYPES = Set.of("回收表", "现场回收", "统一回收", "退货表");

    /** 与 {@link #insertRecycle} 中列清单、ps.set* 绑定个数一致（修改表结构时须同步改此常量与 set 序列）。 */
    private static final int T_RECYCLE_INSERT_BIND_COUNT = 44;

    public static class ImportResult {
        public long batchId;
        public int totalRows;
        public int newRows;
        public int skipRows;
        public int anomalyRows;

        @Override
        public String toString() {
            return String.format("批次ID=%d, 总行数=%d, 新增=%d, 跳过=%d, 异常=%d",
                    batchId, totalRows, newRows, skipRows, anomalyRows);
        }
    }

    /**
     * 使用路径上的文件名作为幂等键中的文件名（CLI）。
     */
    public ImportResult importFile(Path filePath, String fileType) throws Exception {
        return importFile(filePath, filePath.getFileName().toString(), fileType);
    }

    /**
     * @param displayFileName 参与 batch_key 与 t_import_batch.file_name 的原始文件名（Web 上传用 originalFilename）
     */
    public ImportResult importFile(Path filePath, String displayFileName, String fileType) throws Exception {
        SQLException lastSql = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return importFileOnce(filePath, displayFileName, fileType);
            } catch (SQLException e) {
                lastSql = e;
                if (attempt == 0 && ConnectionManager.isSqliteStaleFileError(e)) {
                    logger.warn("检测到数据库文件已变更或连接失效，重置连接池并重试导入一次: {}", e.getMessage());
                    ConnectionManager.reconnectAfterExternalDbChange();
                    continue;
                }
                throw e;
            }
        }
        throw new SQLException("导入在重连后仍失败", lastSql);
    }

    private ImportResult importFileOnce(Path filePath, String displayFileName, String fileType) throws Exception {
        logger.info("开始导入文件: {} (类型: {})", displayFileName, fileType);

        String fileHash = FileHashUtil.sha256(filePath);
        String batchKey = displayFileName + "::" + fileHash;

        if (batchExists(batchKey)) {
            throw new DuplicateImportException("此文件已导入过数据库");
        }

        List<String> headers = ExcelUtil.readHeaders(filePath);
        ExcelFileTypeDetector.validateHeadersForImport(headers, fileType);
        requireOutboundBaselineIfNeeded(fileType);

        ImportResult result = new ImportResult();
        result.skipRows = 0;
        result.anomalyRows = 0;

        Connection conn = null;
        boolean committed = false;
        try {
            conn = ConnectionManager.getConnection();
            conn.setAutoCommit(false);

            long batchId = insertBatchRow(conn, batchKey, displayFileName, fileType);
            result.batchId = batchId;

            Set<String> seenInFile = new HashSet<>();

            try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(filePath))) {
                Sheet sheet = workbook.getSheetAt(0);
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    throw new ImportValidationException("Excel 首行表头为空");
                }

                int lastRow = sheet.getLastRowNum();
                for (int i = 1; i <= lastRow; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null || isRowEffectivelyEmpty(row, headers.size())) {
                        continue;
                    }

                    int rowNum1Based = i + 1;
                    Map<String, String> record = parseRow(row, headerRow, headers);
                    record = DataCleaner.cleanRow(record, fileType);

                    String serialNo = record.get("序列号");
                    if (serialNo == null || serialNo.isEmpty()) {
                        throw new ImportValidationException("第 " + rowNum1Based + " 行序列号为空");
                    }
                    if (seenInFile.contains(serialNo)) {
                        throw new ImportValidationException("文件内序列号重复: " + serialNo);
                    }
                    seenInFile.add(serialNo);

                    validateRecycleRequiredIfNeeded(record, fileType, rowNum1Based);

                    if ("出库单".equals(fileType)) {
                        insertOutbound(conn, record, batchId);
                    } else if ("回收表".equals(fileType)) {
                        String source = resolveRecycleSourceFromRow(record, rowNum1Based);
                        insertRecycle(conn, record, source, batchId);
                    } else if ("现场回收".equals(fileType) || "统一回收".equals(fileType)) {
                        insertRecycle(conn, record, fileType, batchId);
                    } else if ("退货表".equals(fileType)) {
                        insertReturn(conn, record, batchId);
                    } else {
                        throw new IllegalArgumentException("不支持的 fileType: " + fileType);
                    }

                    result.newRows++;
                    result.totalRows++;
                }
            }

            updateBatchStats(conn, batchId, result.totalRows, result.newRows);
            conn.commit();
            committed = true;

            logger.info("文件导入完成: {}", result);
            return result;

        } catch (Exception e) {
            if (conn != null && !committed) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.warn("回滚失败", ex);
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

    /**
     * PRD §5.1.1：无出库主数据时不允许单独导入回收/退货。
     */
    private void requireOutboundBaselineIfNeeded(String fileType) throws SQLException, ImportValidationException {
        if (!RECYCLE_OR_RETURN_TYPES.contains(fileType)) {
            return;
        }
        int n = countOutboundRows();
        if (n <= 0) {
            throw new ImportValidationException(
                    "请先导入销售出库单：当前库中尚无出库主数据，无法导入回收表或退货表。"
                            + "若刚替换或清空过数据库，请先导入销售出库单后再导入其他文件。");
        }
    }

    public static int countOutboundRows() throws SQLException {
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM t_outbound")) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } finally {
            ConnectionManager.returnConnection(conn);
        }
        return 0;
    }

    /**
     * 按行解析「回收方式」→ {@code t_recycle.recycle_source}（现场回收 / 统一回收）。
     */
    static String resolveRecycleSourceFromRow(Map<String, String> record, int rowNum1Based)
            throws ImportValidationException {
        String raw = record.get("回收方式");
        String s = raw == null ? "" : raw.trim();
        if (s.contains("统一")) {
            return "统一回收";
        }
        if (s.contains("现场")) {
            return "现场回收";
        }
        throw new ImportValidationException(
                "第 " + rowNum1Based + " 行「回收方式」无法解析为现场回收或统一回收（须含「现场」或「统一」）"
                        + (raw == null || raw.isEmpty() ? "" : "，当前值: " + raw));
    }

    private static void validateRecycleRequiredIfNeeded(Map<String, String> record, String fileType, int rowNum)
            throws ImportValidationException {
        if (!"回收表".equals(fileType) && !"现场回收".equals(fileType) && !"统一回收".equals(fileType)) {
            return;
        }
        requireNonBlank(record, "单据编码（必填）", rowNum);
        requireNonBlank(record, "扫码回收（必填）", rowNum);
        requireNonBlank(record, "负责人（必填）", rowNum);
    }

    private static void requireNonBlank(Map<String, String> record, String col, int rowNum)
            throws ImportValidationException {
        String v = record.get(col);
        if (v == null || v.trim().isEmpty()) {
            throw new ImportValidationException("第 " + rowNum + " 行「" + col + "」为空");
        }
    }

    private static boolean isRowEffectivelyEmpty(Row row, int headerCount) {
        for (int i = 0; i < headerCount; i++) {
            Cell c = row.getCell(i);
            String v = ExcelUtil.getCellValue(c);
            if (v != null && !v.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> parseRow(Row row, Row headerRow, List<String> headers) {
        Map<String, String> record = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            Cell cell = row.getCell(i);
            String value = ExcelUtil.getCellValue(cell);
            record.put(header, value);
        }
        return record;
    }

    private void insertOutbound(Connection conn, Map<String, String> record, long batchId) throws SQLException {
        String sql = "INSERT OR REPLACE INTO t_outbound (serial_no, material_code, material_name, spec, "
                + "outbound_date, doc_no, order_no, salesperson, sales_dept, customer, end_customer, "
                + "doc_type, description, batch_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.get("序列号"));
            ps.setString(2, record.get("物料编码"));
            ps.setString(3, record.get("物料名称"));
            ps.setString(4, record.get("规格型号"));
            ps.setString(5, DateParser.normalize(record.get("日期")));
            ps.setString(6, record.get("单据编号"));
            ps.setString(7, record.get("订单单号"));
            ps.setString(8, record.get("销售员"));
            ps.setString(9, record.get("销售部门"));
            ps.setString(10, record.get("客户"));
            ps.setString(11, record.get("终端客户"));
            ps.setString(12, record.get("单据类型"));
            ps.setString(13, record.get("描述"));
            ps.setLong(14, batchId);
            ps.executeUpdate();
        }
    }

    private void insertRecycle(Connection conn, Map<String, String> r, String source, long batchId)
            throws SQLException, ImportValidationException {
        String valuesClause = "(" + String.join(",", Collections.nCopies(T_RECYCLE_INSERT_BIND_COUNT, "?")) + ")";
        String sql = "INSERT INTO t_recycle (recycle_source, serial_no, status, remaining_count, "
                + "actual_customer, onsite_recycle_date, onsite_waybill_no, doc_code, spec, recycle_method, "
                + "scan_recycle_code, discount_order_no, scan_code, waybill_no, product_code, erp_order_no, "
                + "terminal_hospital, customer_name, actual_terminal, description, recycle_serial_no, product_name, "
                + "sales_order, sales_outbound_doc, lock_status, recycle_date, outbound_date, erp_outbound_no, "
                + "created_by, created_at, biz_type, dept, owner_dept, owner, life_status, sales_manager, source, "
                + "last_modified_by, last_modified_at, version, group_name, order_date, terminal_org, batch_id) "
                + "VALUES " + valuesClause;

        int remaining = parseRemainingCount(r.get("剩余发数"));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, source);
            ps.setString(i++, r.get("序列号"));
            ps.setString(i++, r.get("状态"));
            ps.setInt(i++, remaining);
            ps.setString(i++, r.get("实际回收客户"));
            ps.setString(i++, DateParser.normalize(r.get("现场实际回收日期")));
            ps.setString(i++, r.get("现场实际回收运单号"));
            ps.setString(i++, r.get("单据编码（必填）"));
            ps.setString(i++, r.get("规格型号"));
            ps.setString(i++, r.get("回收方式"));
            ps.setString(i++, r.get("扫码回收（必填）"));
            ps.setString(i++, r.get("折扣订单指令号"));
            ps.setString(i++, r.get("扫码"));
            ps.setString(i++, r.get("运单单号"));
            ps.setString(i++, r.get("产品编码"));
            ps.setString(i++, r.get("ERP指令号"));
            ps.setString(i++, r.get("终端医院"));
            ps.setString(i++, r.get("客户名称"));
            ps.setString(i++, r.get("实际回收终端"));
            ps.setString(i++, r.get("描述"));
            ps.setString(i++, r.get("回收序列号"));
            ps.setString(i++, r.get("产品名称"));
            ps.setString(i++, r.get("销售订单"));
            ps.setString(i++, r.get("销售出库单"));
            ps.setString(i++, r.get("锁定状态"));
            ps.setString(i++, DateParser.normalize(r.get("回收日期")));
            ps.setString(i++, DateParser.normalize(r.get("出库日期")));
            ps.setString(i++, r.get("ERP出库单号"));
            ps.setString(i++, r.get("创建人"));
            ps.setString(i++, DateParser.normalize(r.get("创建时间")));
            ps.setString(i++, r.get("业务类型"));
            ps.setString(i++, r.get("归属部门"));
            ps.setString(i++, r.get("负责人主属部门"));
            ps.setString(i++, r.get("负责人（必填）"));
            ps.setString(i++, r.get("生命状态"));
            ps.setString(i++, r.get("销售经理"));
            ps.setString(i++, r.get("来源"));
            ps.setString(i++, r.get("最后修改人"));
            ps.setString(i++, DateParser.normalize(r.get("最后修改时间")));
            ps.setString(i++, r.get("版本"));
            ps.setString(i++, r.get("所属集团"));
            ps.setString(i++, DateParser.normalize(r.get("下单日期")));
            ps.setString(i++, r.get("终端机构"));
            ps.setLong(i, batchId);
            ps.executeUpdate();
        }
    }

    private int parseRemainingCount(String raw) throws ImportValidationException {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ImportValidationException("剩余发数格式非法: " + raw);
        }
    }

    private void insertReturn(Connection conn, Map<String, String> record, long batchId) throws SQLException {
        String sql = "INSERT INTO t_return (category, stock_direction, serial_no, material_code, "
                + "material_name, spec, return_date, doc_no, order_no, handler, dept, customer, end_customer, "
                + "return_reason, shipping_address, batch_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.get("类别"));
            ps.setString(2, record.get("库存方向"));
            ps.setString(3, record.get("序列号"));
            ps.setString(4, record.get("物料编码"));
            ps.setString(5, record.get("物料名称"));
            ps.setString(6, record.get("规格型号"));
            ps.setString(7, DateParser.normalize(record.get("日期")));
            ps.setString(8, record.get("单据编号"));
            ps.setString(9, record.get("指令号"));
            ps.setString(10, record.get("领料人"));
            ps.setString(11, record.get("领料部门"));
            ps.setString(12, record.get("客户"));
            ps.setString(13, record.get("终端客户"));
            ps.setString(14, record.get("其他出库类型"));
            ps.setString(15, record.get("收货地址"));
            ps.setLong(16, batchId);
            ps.executeUpdate();
        }
    }

    private boolean batchExists(String batchKey) throws SQLException {
        Connection conn = null;
        try {
            conn = ConnectionManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM t_import_batch WHERE batch_key = ?")) {
                ps.setString(1, batchKey);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } finally {
            ConnectionManager.returnConnection(conn);
        }
    }

    private long insertBatchRow(Connection conn, String batchKey, String fileName, String fileType)
            throws SQLException {
        String sql = "INSERT INTO t_import_batch (batch_key, file_name, file_type, imported_at, total_rows, new_rows, "
                + "skip_rows, anomaly_rows) VALUES (?, ?, ?, ?, 0, 0, 0, 0)";
        String importedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, batchKey);
            ps.setString(2, fileName);
            ps.setString(3, fileType);
            ps.setString(4, importedAt);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create batch record");
    }

    private void updateBatchStats(Connection conn, long batchId, int total, int newRows) throws SQLException {
        String sql = "UPDATE t_import_batch SET total_rows = ?, new_rows = ?, skip_rows = 0, anomaly_rows = 0 WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, total);
            ps.setInt(2, newRows);
            ps.setLong(3, batchId);
            ps.executeUpdate();
        }
    }
}
