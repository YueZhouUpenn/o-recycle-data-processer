package com.company.recycle.util;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件命名策略工具类
 */
public class NamingStrategy {
    
    /**
     * 解析导出路径（自动添加尾缀避免覆盖）
     * 例：销售出库总台账_更新后.xlsx → 销售出库总台账_更新后(1).xlsx
     */
    public static Path resolveExportPath(Path baseDir, String filename) {
        Path candidate = baseDir.resolve(filename);
        
        if (!Files.exists(candidate)) {
            return candidate;
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        String stem = lastDotIndex > 0 ? filename.substring(0, lastDotIndex) : filename;
        String suffix = lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
        
        int n = 1;
        while (Files.exists(candidate)) {
            candidate = baseDir.resolve(stem + "(" + n + ")" + suffix);
            n++;
        }
        
        return candidate;
    }
}
