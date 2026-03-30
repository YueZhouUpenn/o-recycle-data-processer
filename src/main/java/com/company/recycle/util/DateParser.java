package com.company.recycle.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期解析工具类
 */
public class DateParser {
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_OUTPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    /**
     * 规范化日期格式（兼容 YYYY/MM/DD 和 YYYY-MM-DD）
     * 输出统一为 YYYY-MM-DD
     */
    public static String normalize(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return "";
        }
        
        dateStr = dateStr.trim();
        
        // 尝试各种可能的格式
        String[] patterns = {
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy-M-d",
            "yyyy/M/d",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm"
        };
        
        for (String pattern : patterns) {
            try {
                if (pattern.contains("HH:mm")) {
                    LocalDateTime dt = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
                    return dt.format(DATETIME_OUTPUT);
                } else {
                    LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
                    return date.format(OUTPUT_FORMAT);
                }
            } catch (DateTimeParseException ignored) {
                // 尝试下一个格式
            }
        }
        
        // 无法解析，返回原值
        return dateStr;
    }
    
    /**
     * 从 Excel Cell 提取日期
     */
    public static String extractFromCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
            if (dateTime.getHour() == 0 && dateTime.getMinute() == 0 && dateTime.getSecond() == 0) {
                return dateTime.toLocalDate().format(OUTPUT_FORMAT);
            }
            return dateTime.format(DATETIME_OUTPUT);
        } else if (cell.getCellType() == CellType.STRING) {
            return normalize(cell.getStringCellValue());
        }
        
        return "";
    }
}
