package com.company.recycle.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 行数据清洗（与技术方案 §5.3 一致）。
 */
public final class DataCleaner {

    private static final List<String> RECYCLE_DATE_FIELDS = List.of(
            "现场实际回收日期", "回收日期", "出库日期", "创建时间", "最后修改时间", "下单日期");

    private static final Map<String, List<String>> DATE_FIELDS = Map.of(
            "出库单", List.of("日期"),
            "回收表", RECYCLE_DATE_FIELDS,
            "现场回收", RECYCLE_DATE_FIELDS,
            "统一回收", RECYCLE_DATE_FIELDS,
            "退货表", List.of("日期")
    );

    private DataCleaner() {
    }

    public static Map<String, String> cleanRow(Map<String, String> row, String fileType) {
        Map<String, String> cleaned = new HashMap<>(row);

        String serialNo = cleaned.get("序列号");
        if (serialNo != null) {
            cleaned.put("序列号", serialNo.trim().toUpperCase());
        }

        for (String dateField : DATE_FIELDS.getOrDefault(fileType, List.of())) {
            String val = cleaned.get(dateField);
            if (val != null && !val.isEmpty()) {
                cleaned.put(dateField, DateParser.normalize(val));
            }
        }

        if ("回收表".equals(fileType) || "现场回收".equals(fileType) || "统一回收".equals(fileType)) {
            String onsite = getStringOrEmpty(cleaned, "现场实际回收运单号");
            String unified = getStringOrEmpty(cleaned, "运单单号");
            if (onsite.isEmpty() && !unified.isEmpty()) {
                cleaned.put("现场实际回收运单号", unified);
            }
        }

        return cleaned;
    }

    private static String getStringOrEmpty(Map<String, String> map, String key) {
        String val = map.get(key);
        return val != null ? val.trim() : "";
    }
}
