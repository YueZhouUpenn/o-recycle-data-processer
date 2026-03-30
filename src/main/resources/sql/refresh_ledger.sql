WITH recycle_latest AS (
    SELECT r.*
    FROM t_recycle r
    INNER JOIN (
        SELECT serial_no, MAX(id) AS max_id FROM t_recycle GROUP BY serial_no
    ) m ON r.serial_no = m.serial_no AND r.id = m.max_id
),
return_latest AS (
    SELECT t.*
    FROM t_return t
    INNER JOIN (
        SELECT serial_no, MAX(id) AS max_id FROM t_return GROUP BY serial_no
    ) m ON t.serial_no = m.serial_no AND t.id = m.max_id
),
all_sources AS (
    SELECT serial_no, recycle_source AS source, recycle_date, actual_customer FROM t_recycle
    UNION ALL
    SELECT serial_no, '退货回收' AS source, return_date AS recycle_date, customer AS actual_customer FROM t_return
),
source_count AS (
    SELECT
        serial_no,
        COUNT(DISTINCT source) AS cnt,
        GROUP_CONCAT(DISTINCT source) AS sources,
        MAX(recycle_date) AS recycle_date,
        MAX(actual_customer) AS actual_customer
    FROM all_sources
    GROUP BY serial_no
)
INSERT OR REPLACE INTO t_ledger (
    serial_no, material_code, material_name, spec, outbound_date, doc_no, order_no,
    salesperson, sales_dept, customer, end_customer, doc_type, description, outbound_batch_id,
    recycle_status, recycle_source_file, recycle_date, actual_customer, updated_at,
    rc_id, rc_recycle_source, rc_serial_no, rc_status, rc_remaining_count, rc_actual_customer,
    rc_onsite_recycle_date, rc_onsite_waybill_no, rc_doc_code, rc_spec, rc_recycle_method,
    rc_scan_recycle_code, rc_discount_order_no, rc_scan_code, rc_waybill_no, rc_product_code,
    rc_erp_order_no, rc_terminal_hospital, rc_customer_name, rc_actual_terminal, rc_description,
    rc_recycle_serial_no, rc_product_name, rc_sales_order, rc_sales_outbound_doc, rc_lock_status,
    rc_recycle_date, rc_outbound_date, rc_erp_outbound_no, rc_created_by, rc_created_at, rc_biz_type,
    rc_dept, rc_owner_dept, rc_owner, rc_life_status, rc_sales_manager, rc_source,
    rc_last_modified_by, rc_last_modified_at, rc_version, rc_group_name, rc_order_date,
    rc_terminal_org, rc_batch_id,
    rt_id, rt_category, rt_stock_direction, rt_serial_no, rt_material_code, rt_material_name,
    rt_spec, rt_return_date, rt_doc_no, rt_order_no, rt_handler, rt_dept, rt_customer,
    rt_end_customer, rt_return_reason, rt_shipping_address, rt_batch_id
)
SELECT
    o.serial_no,
    o.material_code,
    o.material_name,
    o.spec,
    o.outbound_date,
    o.doc_no,
    o.order_no,
    o.salesperson,
    o.sales_dept,
    o.customer,
    o.end_customer,
    o.doc_type,
    o.description,
    o.batch_id,
    CASE
        WHEN sc.cnt IS NULL THEN '未回收'
        WHEN sc.cnt = 1 THEN sc.sources
        WHEN sc.cnt >= 2 THEN '问题序列号'
    END,
    sc.sources,
    sc.recycle_date,
    sc.actual_customer,
    datetime('now', 'localtime'),
    rc.id,
    rc.recycle_source,
    rc.serial_no,
    rc.status,
    rc.remaining_count,
    rc.actual_customer,
    rc.onsite_recycle_date,
    rc.onsite_waybill_no,
    rc.doc_code,
    rc.spec,
    rc.recycle_method,
    rc.scan_recycle_code,
    rc.discount_order_no,
    rc.scan_code,
    rc.waybill_no,
    rc.product_code,
    rc.erp_order_no,
    rc.terminal_hospital,
    rc.customer_name,
    rc.actual_terminal,
    rc.description,
    rc.recycle_serial_no,
    rc.product_name,
    rc.sales_order,
    rc.sales_outbound_doc,
    rc.lock_status,
    rc.recycle_date,
    rc.outbound_date,
    rc.erp_outbound_no,
    rc.created_by,
    rc.created_at,
    rc.biz_type,
    rc.dept,
    rc.owner_dept,
    rc.owner,
    rc.life_status,
    rc.sales_manager,
    rc.source,
    rc.last_modified_by,
    rc.last_modified_at,
    rc.version,
    rc.group_name,
    rc.order_date,
    rc.terminal_org,
    rc.batch_id,
    rt.id,
    rt.category,
    rt.stock_direction,
    rt.serial_no,
    rt.material_code,
    rt.material_name,
    rt.spec,
    rt.return_date,
    rt.doc_no,
    rt.order_no,
    rt.handler,
    rt.dept,
    rt.customer,
    rt.end_customer,
    rt.return_reason,
    rt.shipping_address,
    rt.batch_id
FROM t_outbound o
LEFT JOIN source_count sc ON o.serial_no = sc.serial_no
LEFT JOIN recycle_latest rc ON o.serial_no = rc.serial_no
LEFT JOIN return_latest rt ON o.serial_no = rt.serial_no
