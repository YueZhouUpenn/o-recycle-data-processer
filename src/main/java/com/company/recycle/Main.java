package com.company.recycle;

import com.company.recycle.db.ConnectionManager;
import com.company.recycle.db.DatabaseInitializer;
import com.company.recycle.exporter.LedgerExporter;
import com.company.recycle.pipeline.FileImporter;
import com.company.recycle.pipeline.LedgerRefresher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * 主程序入口
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("  财务回收对账系统 v1.0.0");
        logger.info("========================================");
        
        try {
            // 初始化数据库
            ConnectionManager.initialize();
            DatabaseInitializer.initialize();
            
            // 交互式菜单
            runInteractiveMode();
            
        } catch (Exception e) {
            logger.error("程序运行出错", e);
            System.err.println("程序运行出错: " + e.getMessage());
        } finally {
            ConnectionManager.shutdown();
        }
    }
    
    private static void runInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        FileImporter importer = new FileImporter();
        
        while (true) {
            System.out.println("\n======= 主菜单 =======");
            System.out.println("1. 导入文件");
            System.out.println("2. 刷新总台账");
            System.out.println("3. 导出总台账");
            System.out.println("4. 查看统计信息");
            System.out.println("5. 快速导入测试数据");
            System.out.println("6. 启动Web界面");
            System.out.println("0. 退出");
            System.out.print("\n请选择操作: ");
            
            String choice = scanner.nextLine().trim();
            
            try {
                switch (choice) {
                    case "1" -> importFile(scanner, importer);
                    case "2" -> refreshLedger();
                    case "3" -> exportLedger();
                    case "4" -> showStatistics();
                    case "5" -> quickImportTestData(importer);
                    case "6" -> startWebServer();
                    case "0" -> {
                        System.out.println("再见！");
                        return;
                    }
                    default -> System.out.println("无效选择，请重新输入");
                }
            } catch (Exception e) {
                logger.error("操作失败", e);
                System.err.println("操作失败: " + e.getMessage());
            }
        }
    }
    
    private static void startWebServer() {
        System.out.println("\n正在启动Web服务器...");
        System.out.println("提示：启动后请访问 http://localhost:8080");
        System.out.println("按 Ctrl+C 可停止服务器\n");
        
        try {
            com.company.recycle.web.WebApplication.main(new String[]{});
        } catch (Exception e) {
            logger.error("Web服务器启动失败", e);
            System.err.println("Web服务器启动失败: " + e.getMessage());
        }
    }
    
    private static void importFile(Scanner scanner, FileImporter importer) throws Exception {
        System.out.println("\n文件类型:");
        System.out.println("1. 出库单");
        System.out.println("2. 回收表（现场/统一由行内「回收方式」解析）");
        System.out.println("3. 退货表");
        System.out.print("请选择文件类型: ");
        
        String typeChoice = scanner.nextLine().trim();
        String fileType = switch (typeChoice) {
            case "1" -> "出库单";
            case "2" -> "回收表";
            case "3" -> "退货表";
            default -> {
                System.out.println("无效选择");
                yield null;
            }
        };
        
        if (fileType == null) return;
        
        System.out.print("请输入文件路径: ");
        String filePath = scanner.nextLine().trim();
        
        Path path = Paths.get(filePath);
        if (!path.toFile().exists()) {
            System.out.println("文件不存在: " + filePath);
            return;
        }
        
        System.out.println("\n开始导入...");
        FileImporter.ImportResult result = importer.importFile(path, fileType);
        
        System.out.println("\n✓ 导入完成！");
        System.out.println("  批次ID: " + result.batchId);
        System.out.println("  总行数: " + result.totalRows);
        System.out.println("  新增行数: " + result.newRows);
        System.out.println("  跳过行数: " + result.skipRows);
        System.out.println("  异常行数: " + result.anomalyRows);
        
        // 自动刷新台账
        System.out.print("\n是否立即刷新总台账? (Y/n): ");
        String refresh = scanner.nextLine().trim();
        if (refresh.isEmpty() || refresh.equalsIgnoreCase("Y")) {
            refreshLedger();
        }
    }
    
    private static void refreshLedger() throws Exception {
        System.out.println("\n开始刷新总台账...");
        LedgerRefresher.refreshLedger();
        System.out.println("✓ 总台账刷新完成！");
    }
    
    private static void exportLedger() throws Exception {
        System.out.println("\n开始导出总台账...");
        Path outputPath = LedgerExporter.exportLedger(null);
        System.out.println("✓ 导出完成！");
        System.out.println("  文件路径: " + outputPath.toAbsolutePath());
    }
    
    private static void showStatistics() throws Exception {
        System.out.println("\n========== 统计信息 ==========");
        LedgerExporter.Statistics stats = LedgerExporter.getStatistics();
        System.out.println("  总出库数: " + stats.totalOutbound);
        System.out.println("  已回收数: " + stats.recycled);
        System.out.println("  未回收数: " + stats.unrecycled);
        System.out.println("  问题序列号: " + stats.problem);
        System.out.println("  回收率: " + String.format("%.2f%%", stats.recycleRate));
        System.out.println("===============================");
    }
    
    private static void quickImportTestData(FileImporter importer) throws Exception {
        System.out.println("\n开始快速导入测试数据（input_test目录）...");
        
        String[] files = {
            "input_test/销售出库单.xlsx|出库单",
            "input_test/现场回收.xlsx|回收表",
            "input_test/统一回收.xlsx|回收表",
            "input_test/退货表.xlsx|退货表"
        };
        
        int successCount = 0;
        for (String fileInfo : files) {
            String[] parts = fileInfo.split("\\|");
            String filePath = parts[0];
            String fileType = parts[1];
            
            Path path = Paths.get(filePath);
            if (!path.toFile().exists()) {
                System.out.println("  ⚠ 跳过: " + filePath + " (文件不存在)");
                continue;
            }
            
            try {
                System.out.println("  导入: " + filePath);
                FileImporter.ImportResult result = importer.importFile(path, fileType);
                System.out.println("    ✓ 成功 (新增: " + result.newRows + ", 跳过: " + result.skipRows + ")");
                successCount++;
            } catch (Exception e) {
                System.out.println("    ✗ 失败: " + e.getMessage());
            }
        }
        
        if (successCount > 0) {
            System.out.println("\n刷新总台账...");
            LedgerRefresher.refreshLedger();
            System.out.println("✓ 全部完成！成功导入 " + successCount + " 个文件");
            
            showStatistics();
        } else {
            System.out.println("没有成功导入任何文件");
        }
    }
}
