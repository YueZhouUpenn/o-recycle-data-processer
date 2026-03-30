-- 销售出库主表
CREATE TABLE IF NOT EXISTS t_outbound (
    serial_no       TEXT    PRIMARY KEY,
    material_code   TEXT,
    material_name   TEXT,
    spec            TEXT,
    outbound_date   TEXT,
    doc_no          TEXT,
    order_no        TEXT,
    salesperson     TEXT,
    sales_dept      TEXT,
    customer        TEXT,
    end_customer    TEXT,
    doc_type        TEXT,
    description     TEXT,
    batch_id        INTEGER,
    FOREIGN KEY (batch_id) REFERENCES t_import_batch(id)
);

-- 现场回收/统一回收明细表
CREATE TABLE IF NOT EXISTS t_recycle (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    recycle_source          TEXT    NOT NULL,
    serial_no               TEXT    NOT NULL,
    status                  TEXT,
    remaining_count         INTEGER,
    actual_customer         TEXT,
    onsite_recycle_date     TEXT,
    onsite_waybill_no       TEXT,
    doc_code                TEXT,
    spec                    TEXT,
    recycle_method          TEXT,
    scan_recycle_code       TEXT,
    discount_order_no       TEXT,
    scan_code               TEXT,
    waybill_no              TEXT,
    product_code            TEXT,
    erp_order_no            TEXT,
    terminal_hospital       TEXT,
    customer_name           TEXT,
    actual_terminal         TEXT,
    description             TEXT,
    recycle_serial_no       TEXT,
    product_name            TEXT,
    sales_order             TEXT,
    sales_outbound_doc      TEXT,
    lock_status             TEXT,
    recycle_date            TEXT,
    outbound_date           TEXT,
    erp_outbound_no         TEXT,
    created_by              TEXT,
    created_at              TEXT,
    biz_type                TEXT,
    dept                    TEXT,
    owner_dept              TEXT,
    owner                   TEXT,
    life_status             TEXT,
    sales_manager           TEXT,
    source                  TEXT,
    last_modified_by        TEXT,
    last_modified_at        TEXT,
    version                 TEXT,
    group_name              TEXT,
    order_date              TEXT,
    terminal_org            TEXT,
    batch_id                INTEGER,
    FOREIGN KEY (batch_id) REFERENCES t_import_batch(id)
);

-- 退货回收明细表
CREATE TABLE IF NOT EXISTS t_return (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    category            TEXT,
    stock_direction     TEXT,
    serial_no           TEXT    NOT NULL,
    material_code       TEXT,
    material_name       TEXT,
    spec                TEXT,
    return_date         TEXT,
    doc_no              TEXT,
    order_no            TEXT,
    handler             TEXT,
    dept                TEXT,
    customer            TEXT,
    end_customer        TEXT,
    return_reason       TEXT,
    shipping_address    TEXT,
    batch_id            INTEGER,
    FOREIGN KEY (batch_id) REFERENCES t_import_batch(id)
);

-- 总台账（物化宽表：出库字段 + 系统状态 + rc_* + rt_*，见 TECH_DESIGN §3.4）
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
);

-- 导入批次日志
CREATE TABLE IF NOT EXISTS t_import_batch (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    batch_key   TEXT    NOT NULL UNIQUE,
    file_name   TEXT    NOT NULL,
    file_type   TEXT    NOT NULL,
    imported_at TEXT    NOT NULL,
    total_rows  INTEGER NOT NULL DEFAULT 0,
    new_rows    INTEGER NOT NULL DEFAULT 0,
    skip_rows   INTEGER NOT NULL DEFAULT 0,
    anomaly_rows INTEGER NOT NULL DEFAULT 0
);

-- 异常记录表
CREATE TABLE IF NOT EXISTS t_anomaly (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    serial_no       TEXT,
    anomaly_type    TEXT    NOT NULL,
    detail          TEXT,
    source_files    TEXT,
    batch_id        INTEGER,
    created_at      TEXT    NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES t_import_batch(id)
);

-- 数据库版本管理
CREATE TABLE IF NOT EXISTS t_schema_version (
    version     INTEGER PRIMARY KEY,
    applied_at  TEXT NOT NULL,
    description TEXT
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_outbound_serial     ON t_outbound(serial_no);
CREATE INDEX IF NOT EXISTS idx_outbound_date       ON t_outbound(outbound_date);
CREATE INDEX IF NOT EXISTS idx_outbound_salesperson ON t_outbound(salesperson);
CREATE INDEX IF NOT EXISTS idx_outbound_customer   ON t_outbound(end_customer);

CREATE INDEX IF NOT EXISTS idx_recycle_serial      ON t_recycle(serial_no);
CREATE INDEX IF NOT EXISTS idx_recycle_source      ON t_recycle(recycle_source);

CREATE INDEX IF NOT EXISTS idx_return_serial       ON t_return(serial_no);

CREATE INDEX IF NOT EXISTS idx_ledger_status       ON t_ledger(recycle_status);
CREATE INDEX IF NOT EXISTS idx_ledger_date         ON t_ledger(outbound_date);
CREATE INDEX IF NOT EXISTS idx_ledger_salesperson  ON t_ledger(salesperson);
CREATE INDEX IF NOT EXISTS idx_ledger_customer     ON t_ledger(end_customer);

CREATE INDEX IF NOT EXISTS idx_anomaly_type        ON t_anomaly(anomaly_type);
CREATE INDEX IF NOT EXISTS idx_anomaly_batch       ON t_anomaly(batch_id);

-- 初始版本
INSERT OR IGNORE INTO t_schema_version VALUES (1, datetime('now','localtime'), 'initial schema');
