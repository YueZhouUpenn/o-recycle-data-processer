package com.company.recycle.db;

/**
 * 宽表 t_ledger：出库字段保持与 PRD 一致（无前缀），回收/退货字段加 rc_/rt_ 前缀。
 * 与 {@code schema.sql} 中 t_ledger 定义须保持一致。
 */
public final class LedgerSchema {

    private LedgerSchema() {
    }

    /**
     * 建表 DDL（用于迁移时 DROP 后重建；须与 resources/schema.sql 中 t_ledger 块同步）。
     */
    public static final String CREATE_LEDGER_WIDE = """
            CREATE TABLE IF NOT EXISTS t_ledger (
                serial_no               TEXT    PRIMARY KEY,
                material_code           TEXT,
                material_name           TEXT,
                spec                    TEXT,
                outbound_date           TEXT,
                doc_no                  TEXT,
                order_no                TEXT,
                salesperson             TEXT,
                sales_dept              TEXT,
                customer                TEXT,
                end_customer            TEXT,
                doc_type                TEXT,
                description             TEXT,
                outbound_batch_id       INTEGER,
                recycle_status          TEXT    NOT NULL DEFAULT '未回收',
                recycle_source_file     TEXT,
                recycle_date            TEXT,
                actual_customer         TEXT,
                updated_at              TEXT,
                rc_id                   INTEGER,
                rc_recycle_source       TEXT,
                rc_serial_no            TEXT,
                rc_status               TEXT,
                rc_remaining_count      INTEGER,
                rc_actual_customer      TEXT,
                rc_onsite_recycle_date  TEXT,
                rc_onsite_waybill_no    TEXT,
                rc_doc_code             TEXT,
                rc_spec                 TEXT,
                rc_recycle_method       TEXT,
                rc_scan_recycle_code    TEXT,
                rc_discount_order_no    TEXT,
                rc_scan_code            TEXT,
                rc_waybill_no           TEXT,
                rc_product_code         TEXT,
                rc_erp_order_no         TEXT,
                rc_terminal_hospital    TEXT,
                rc_customer_name        TEXT,
                rc_actual_terminal      TEXT,
                rc_description          TEXT,
                rc_recycle_serial_no    TEXT,
                rc_product_name         TEXT,
                rc_sales_order          TEXT,
                rc_sales_outbound_doc   TEXT,
                rc_lock_status          TEXT,
                rc_recycle_date         TEXT,
                rc_outbound_date        TEXT,
                rc_erp_outbound_no      TEXT,
                rc_created_by           TEXT,
                rc_created_at           TEXT,
                rc_biz_type             TEXT,
                rc_dept                 TEXT,
                rc_owner_dept           TEXT,
                rc_owner                TEXT,
                rc_life_status          TEXT,
                rc_sales_manager        TEXT,
                rc_source               TEXT,
                rc_last_modified_by     TEXT,
                rc_last_modified_at     TEXT,
                rc_version              TEXT,
                rc_group_name           TEXT,
                rc_order_date           TEXT,
                rc_terminal_org         TEXT,
                rc_batch_id             INTEGER,
                rt_id                   INTEGER,
                rt_category             TEXT,
                rt_stock_direction      TEXT,
                rt_serial_no            TEXT,
                rt_material_code        TEXT,
                rt_material_name        TEXT,
                rt_spec                 TEXT,
                rt_return_date          TEXT,
                rt_doc_no               TEXT,
                rt_order_no             TEXT,
                rt_handler              TEXT,
                rt_dept                 TEXT,
                rt_customer             TEXT,
                rt_end_customer         TEXT,
                rt_return_reason        TEXT,
                rt_shipping_address     TEXT,
                rt_batch_id             INTEGER
            )
            """;

    /** 是否已为宽表（存在 rc_id 列）。 */
    public static boolean isWideLedger(java.sql.Connection conn) throws java.sql.SQLException {
        try (var st = conn.createStatement();
             var rs = st.executeQuery("PRAGMA table_info(t_ledger)")) {
            while (rs.next()) {
                if ("rc_id".equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
