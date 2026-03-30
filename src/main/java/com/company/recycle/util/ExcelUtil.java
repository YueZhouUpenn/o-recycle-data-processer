package com.company.recycle.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Excel 工具类
 */
public class ExcelUtil {
    
    /**
     * 读取 Excel 表头
     */
    public static List<String> readHeaders(Path filePath) throws IOException {
        try (InputStream in = Files.newInputStream(filePath)) {
            return readHeaders(in);
        }
    }

    /**
     * 从流读取首行表头（调用方负责关闭流；若需多次读取请使用 ByteArrayInputStream 或临时文件）。
     */
    public static List<String> readHeaders(InputStream inputStream) throws IOException {
        List<String> headers = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    headers.add(getCellValue(cell));
                }
            }
        }
        return headers;
    }
    
    /**
     * 获取单元格值（统一转为字符串）
     */
    public static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return DateParser.extractFromCell(cell);
                } else {
                    double numValue = cell.getNumericCellValue();
                    // 如果是整数，不显示小数点
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    }
                    return String.valueOf(numValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
            default:
                return "";
        }
    }
}
