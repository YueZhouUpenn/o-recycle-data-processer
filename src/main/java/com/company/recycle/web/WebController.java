package com.company.recycle.web;

import com.company.recycle.exception.ColumnMissingException;
import com.company.recycle.exception.DuplicateImportException;
import com.company.recycle.exception.ImportValidationException;
import com.company.recycle.exporter.LedgerExporter;
import com.company.recycle.pipeline.FileImporter;
import com.company.recycle.pipeline.LedgerRefresher;
import com.company.recycle.util.ExcelFileTypeDetector;
import com.company.recycle.util.ExcelUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Web API Controller
 */
@RestController
@RequestMapping("/api")
public class WebController {
    private static final Logger logger = LoggerFactory.getLogger(WebController.class);
    private final DataService dataService = new DataService();
    private final FileImporter fileImporter = new FileImporter();

    private static final Set<String> ALLOWED_IMPORT_TYPES = Set.of("出库单", "回收表", "现场回收", "统一回收", "退货表");

    @Value("${recycle.import.auto-refresh:true}")
    private boolean importAutoRefresh;
    
    /**
     * 获取总台账数据（分页）；筛选条件均为可选，组合为 SQL WHERE（见 {@link LedgerFilter}）。
     */
    @GetMapping("/ledger")
    public ResponseEntity<Map<String, Object>> getLedger(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String serialNo,
            @RequestParam(required = false) String salesperson,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String recycleDateStart,
            @RequestParam(required = false) String recycleDateEnd) {

        try {
            long start = System.currentTimeMillis();
            LedgerFilter filter = LedgerFilter.fromRequest(
                    serialNo, salesperson, customer, recycleDateStart, recycleDateEnd);
            logger.info("接口请求: GET /api/ledger, page={}, pageSize={}, filter=[serialNo={}, salesperson={}, customer={}, recycleDateStart={}, recycleDateEnd={}]",
                    page, pageSize, serialNo, salesperson, customer, recycleDateStart, recycleDateEnd);

            List<Map<String, Object>> data = dataService.getLedgerData(page, pageSize, filter);
            int total = dataService.getLedgerCount(filter);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("data", data);
            response.put("pagination", Map.of(
                    "page", page,
                    "pageSize", pageSize,
                    "total", total,
                    "totalPages", (int) Math.ceil((double) total / pageSize)
            ));
            logger.info("接口返回: GET /api/ledger, dataSize={}, total={}, totalPages={}, costMs={}",
                    data.size(), total, (int) Math.ceil((double) total / pageSize), System.currentTimeMillis() - start);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取总台账数据失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "获取数据失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * PRD §5.8 条件回收率：与 {@link #getLedger} 相同筛选条件下的行数、未回收数、回收率。
     */
    @GetMapping("/ledger/conditional-summary")
    public ResponseEntity<Map<String, Object>> ledgerConditionalSummary(
            @RequestParam(required = false) String serialNo,
            @RequestParam(required = false) String salesperson,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String recycleDateStart,
            @RequestParam(required = false) String recycleDateEnd) {
        try {
            LedgerFilter filter = LedgerFilter.fromRequest(
                    serialNo, salesperson, customer, recycleDateStart, recycleDateEnd);
            Map<String, Object> summary = dataService.getConditionalSummary(filter);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.putAll(summary);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("条件回收率汇总失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 导入前置状态：出库主表是否有数据（PRD §5.1.1）。
     */
    @GetMapping("/import/baseline")
    public ResponseEntity<Map<String, Object>> importBaseline() {
        try {
            int n = dataService.getOutboundCount();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("outboundRowCount", n);
            body.put("recycleReturnImportAllowed", n > 0);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("读取导入前置状态失败", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    /**
     * 上传单个 Excel，根据首行表头识别四类（与存量模板列名完全一致；回收类表头统一为「回收表」，行内「回收方式」区分现场/统一）。
     */
    @PostMapping(value = "/import/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> detectImportFileType(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("success", false);
                err.put("message", "请选择文件");
                return ResponseEntity.badRequest().body(err);
            }
            List<String> headers;
            try (InputStream in = file.getInputStream()) {
                headers = ExcelUtil.readHeaders(in);
            }
            ExcelFileTypeDetector.Result r = ExcelFileTypeDetector.detect(headers);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("fileName", file.getOriginalFilename());
            body.put("status", r.status.name());
            body.put("importerType", r.importerType);
            body.put("displayLabel", r.displayLabel);
            body.put("message", r.message);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("表头识别失败", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "无法读取表头: " + e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    /**
     * 正式入库：单文件单事务；fileType 须与表头识别结果一致（回收表由行内字段解析来源）。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType) {
        Map<String, Object> err = new LinkedHashMap<>();
        if (file == null || file.isEmpty()) {
            err.put("success", false);
            err.put("message", "请选择文件");
            return ResponseEntity.badRequest().body(err);
        }
        String type = fileType == null ? "" : fileType.trim();
        if (!ALLOWED_IMPORT_TYPES.contains(type)) {
            err.put("success", false);
            err.put("message", "fileType 无效，须为：出库单、回收表、现场回收、统一回收、退货表");
            return ResponseEntity.badRequest().body(err);
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "upload.xlsx";
        }

        Path temp = null;
        try {
            temp = Files.createTempFile("recycle-import-", ".xlsx");
            file.transferTo(temp.toFile());

            FileImporter.ImportResult result = fileImporter.importFile(temp, originalName, type);

            if (importAutoRefresh) {
                LedgerRefresher.refreshLedger();
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            String msg;
            if ("出库单".equals(type)) {
                msg = String.format("出库表：已导入%d行数据，排除%d行重复序列号", result.newRows, result.skipRows);
                if (importAutoRefresh) {
                    msg += "，已自动刷新总台账";
                }
            } else if ("退货表".equals(type)) {
                msg = String.format("退货表：已导入%d行数据，排除%d行重复序列号", result.newRows, result.skipRows);
                if (importAutoRefresh) {
                    msg += "，已自动刷新总台账";
                }
            } else {
                msg = importAutoRefresh ? "导入成功，已自动刷新总台账" : "导入成功";
            }
            body.put("message", msg);
            body.put("batchId", result.batchId);
            body.put("totalRows", result.totalRows);
            body.put("newRows", result.newRows);
            body.put("skipRows", result.skipRows);
            body.put("ledgerRefreshed", importAutoRefresh);
            return ResponseEntity.ok(body);

        } catch (DuplicateImportException e) {
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(err);
        } catch (ColumnMissingException | ImportValidationException e) {
            err.put("success", false);
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            logger.error("导入失败", e);
            err.put("success", false);
            err.put("message", "导入失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (Exception ex) {
                    logger.debug("删除临时文件失败: {}", temp, ex);
                }
            }
        }
    }

    @PostMapping("/ledger/refresh")
    public ResponseEntity<Map<String, Object>> refreshLedger() {
        try {
            logger.info("接口请求: POST /api/ledger/refresh");
            LedgerRefresher.refreshLedger();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("message", "总台账已根据当前业务表重算完成");
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("刷新总台账失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "刷新失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * 获取统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            long start = System.currentTimeMillis();
            logger.info("接口请求: GET /api/statistics");
            Map<String, Object> stats = dataService.getStatistics();
            List<Map<String, Object>> distribution = dataService.getStatusDistribution();
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("statistics", stats);
            response.put("distribution", distribution);
            logger.info("接口返回: GET /api/statistics, statsKeys={}, distributionSize={}, costMs={}",
                    stats.keySet(), distribution.size(), System.currentTimeMillis() - start);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "获取统计信息失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 异常表 {@code t_anomaly} 全列分页列表（PRD「异常信息」Tab）。
     */
    @GetMapping("/anomalies")
    public ResponseEntity<Map<String, Object>> getAnomalies(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        try {
            List<Map<String, Object>> data = dataService.getAnomalies(page, pageSize);
            int total = dataService.getAnomalyCount();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("data", data);
            body.put("pagination", Map.of(
                    "page", page,
                    "pageSize", pageSize,
                    "total", total,
                    "totalPages", Math.max(1, (int) Math.ceil((double) total / pageSize))
            ));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("读取异常表失败", e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "读取异常信息失败: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * 导出 Excel；查询参数与 {@link #getLedger} 一致时导出当前筛选结果，否则导出全表。
     */
    @GetMapping("/export")
    public ResponseEntity<Resource> exportExcel(
            @RequestParam(required = false) String serialNo,
            @RequestParam(required = false) String salesperson,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String recycleDateStart,
            @RequestParam(required = false) String recycleDateEnd) {
        try {
            long start = System.currentTimeMillis();
            LedgerFilter filter = LedgerFilter.fromRequest(
                    serialNo, salesperson, customer, recycleDateStart, recycleDateEnd);
            logger.info("接口请求: GET /api/export (filter 与列表一致)");
            Path filePath = LedgerExporter.exportLedger(filter);
            Resource resource = new FileSystemResource(filePath.toFile());
            
            String filename = filePath.getFileName().toString();
            long fileSize = filePath.toFile().length();
            logger.info("导出文件已生成, path={}, filename={}, sizeBytes={}, exists={}",
                    filePath, filename, fileSize, filePath.toFile().exists());
            
            ResponseEntity<Resource> response = ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + new String(filename.getBytes("UTF-8"), "ISO-8859-1") + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
            logger.info("接口返回: GET /api/export, costMs={}", System.currentTimeMillis() - start);
            return response;
                    
        } catch (Exception e) {
            logger.error("导出Excel失败", e);
            return ResponseEntity.status(500).build();
        }
    }
}
