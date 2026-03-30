package com.company.recycle.util;

import com.company.recycle.exception.ColumnMissingException;

import java.util.*;

/**
 * 根据首行表头判断 Excel 属于四类中的哪一类（与存量模板一致方可识别）。
 * 现场回收与统一回收共用同一套 42 列表头，表头侧统一识别为「回收表」；行内「回收方式」再区分现场/统一（见 {@link com.company.recycle.pipeline.FileImporter}）。
 */
public final class ExcelFileTypeDetector {

    public enum Status {
        /** 已唯一确定导入类型 */
        MATCHED,
        /** 无法与任一类模板对齐 */
        UNKNOWN
    }

    public static final class Result {
        public final Status status;
        /** FileImporter 使用的 fileType：出库单 / 退货表 / 回收表（及兼容：现场回收 / 统一回收）；UNKNOWN 时为 null */
        public final String importerType;
        /** 界面展示用四类名称 */
        public final String displayLabel;
        public final String message;

        private Result(Status status, String importerType, String displayLabel, String message) {
            this.status = status;
            this.importerType = importerType;
            this.displayLabel = displayLabel;
            this.message = message;
        }

        static Result matched(String importerType, String displayLabel) {
            return new Result(Status.MATCHED, importerType, displayLabel, null);
        }

        static Result unknown() {
            return new Result(Status.UNKNOWN, null, null,
                    "表头与四类标准模板均不一致，请使用业务系统导出的销售出库单 / 回收表 / 退货表原始表头文件。");
        }
    }

    private static final Set<String> OUTBOUND_REQUIRED = Set.of(
            "序列号", "物料编码", "物料名称", "规格型号", "日期", "单据编号", "订单单号",
            "销售员", "销售部门", "客户", "终端客户", "单据类型", "描述");

    private static final Set<String> RETURN_REQUIRED = Set.of(
            "类别", "库存方向", "序列号", "物料编码", "物料名称", "规格型号", "日期", "单据编号",
            "指令号", "领料人", "领料部门", "客户", "终端客户", "其他出库类型", "收货地址");

    private static final Set<String> RECYCLE_REQUIRED = Set.of(
            "序列号", "状态", "剩余发数", "实际回收客户", "现场实际回收日期", "现场实际回收运单号",
            "单据编码（必填）", "规格型号", "回收方式", "扫码回收（必填）", "折扣订单指令号", "扫码",
            "运单单号", "产品编码", "ERP指令号", "终端医院", "客户名称", "实际回收终端", "描述",
            "回收序列号", "产品名称", "销售订单", "销售出库单", "锁定状态", "回收日期", "出库日期",
            "ERP出库单号", "创建人", "创建时间", "业务类型", "归属部门", "负责人主属部门",
            "负责人（必填）", "生命状态", "销售经理", "来源", "最后修改人", "最后修改时间",
            "版本", "所属集团", "下单日期", "终端机构");

    private ExcelFileTypeDetector() {
    }

    /**
     * 校验表头是否包含该 fileType 所需的全部列名（与 {@link #detect} 使用同一套标准列）。
     *
     * @throws ColumnMissingException 缺列时
     */
    public static void validateHeadersForImport(List<String> headers, String fileType)
            throws ColumnMissingException {
        Set<String> required = requiredHeaderSet(fileType);
        if (required == null) {
            throw new IllegalArgumentException("未知 fileType: " + fileType);
        }
        Set<String> h = new HashSet<>();
        if (headers != null) {
            for (String s : headers) {
                if (s != null) {
                    String t = s.trim();
                    if (!t.isEmpty()) {
                        h.add(t);
                    }
                }
            }
        }
        if (!h.containsAll(required)) {
            Set<String> missing = new HashSet<>(required);
            missing.removeAll(h);
            throw new ColumnMissingException(
                    fileType + " 文件表头缺少必填列: " + String.join("、", missing));
        }
        if (!h.contains("序列号")) {
            throw new ColumnMissingException(
                    fileType + "文件缺少序列号列，请确认文件格式后重新导入");
        }
    }

    private static Set<String> requiredHeaderSet(String fileType) {
        return switch (fileType) {
            case "出库单" -> OUTBOUND_REQUIRED;
            case "退货表" -> RETURN_REQUIRED;
            case "回收表", "现场回收", "统一回收" -> RECYCLE_REQUIRED;
            default -> null;
        };
    }

    /**
     * @param headers 首行表头（与 Excel 列顺序无关，按名称集合匹配）
     */
    public static Result detect(List<String> headers) {
        Set<String> h = new HashSet<>();
        if (headers != null) {
            for (String s : headers) {
                if (s != null) {
                    String t = s.trim();
                    if (!t.isEmpty()) {
                        h.add(t);
                    }
                }
            }
        }

        // 1. 退货表：15 列特征明显（类别、库存方向、其他出库类型）
        if (h.containsAll(RETURN_REQUIRED) && h.contains("类别") && h.contains("库存方向")) {
            return Result.matched("退货表", "退货表");
        }

        // 2. 销售出库单：13 列（与 FileImporter 出库单一致）
        if (h.containsAll(OUTBOUND_REQUIRED) && h.contains("订单单号") && h.contains("单据类型")) {
            return Result.matched("出库单", "销售出库单");
        }

        // 3. 回收表：42 列与现场/统一业务导出一致，表头不区分来源
        if (h.containsAll(RECYCLE_REQUIRED)) {
            return Result.matched("回收表", "回收表");
        }

        return Result.unknown();
    }
}
