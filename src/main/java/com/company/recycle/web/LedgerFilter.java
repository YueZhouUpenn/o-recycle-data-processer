package com.company.recycle.web;

import java.util.List;

/**
 * 总台账列表/导出/条件回收率共用的筛选条件（与 PRD §5.8 一致）。
 * 未填写的维度不参与过滤；若填写回收起止日之一或二者，则仅统计/列出 {@code recycle_date} 非空且落在闭区间内的行。
 */
public final class LedgerFilter {

    public final String serialNo;
    public final String salesperson;
    public final String customer;
    public final String recycleDateStart;
    public final String recycleDateEnd;

    private LedgerFilter(String serialNo, String salesperson, String customer,
            String recycleDateStart, String recycleDateEnd) {
        this.serialNo = serialNo;
        this.salesperson = salesperson;
        this.customer = customer;
        this.recycleDateStart = recycleDateStart;
        this.recycleDateEnd = recycleDateEnd;
    }

    public static LedgerFilter fromRequest(String serialNo, String salesperson, String customer,
            String recycleDateStart, String recycleDateEnd) {
        return new LedgerFilter(
                trimToNull(serialNo),
                trimToNull(salesperson),
                trimToNull(customer),
                trimToNull(recycleDateStart),
                trimToNull(recycleDateEnd)
        );
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 追加 {@code WHERE 1=1} 及条件；{@code params} 按占位符顺序填充。
     */
    public void appendWhere(StringBuilder sb, List<Object> params) {
        sb.append(" WHERE 1=1 ");
        if (serialNo != null) {
            sb.append(" AND serial_no LIKE ? ESCAPE '\\' ");
            params.add("%" + escapeLike(serialNo) + "%");
        }
        if (salesperson != null) {
            sb.append(" AND salesperson LIKE ? ESCAPE '\\' ");
            params.add("%" + escapeLike(salesperson) + "%");
        }
        if (customer != null) {
            sb.append(" AND customer LIKE ? ESCAPE '\\' ");
            params.add("%" + escapeLike(customer) + "%");
        }
        boolean dateRange = recycleDateStart != null || recycleDateEnd != null;
        if (dateRange) {
            sb.append(" AND recycle_date IS NOT NULL AND TRIM(recycle_date) != '' ");
            if (recycleDateStart != null) {
                sb.append(" AND recycle_date >= ? ");
                params.add(recycleDateStart);
            }
            if (recycleDateEnd != null) {
                sb.append(" AND recycle_date <= ? ");
                params.add(recycleDateEnd);
            }
        }
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
