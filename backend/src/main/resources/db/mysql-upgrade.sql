-- 已有 MySQL 库从旧版本升级时手工执行(每列执行一次; 列已存在报 1060 可忽略)。全新库直接由 schema.sql 建表, 无需本脚本。
ALTER TABLE wms_asn ADD COLUMN cross_dock_order_code VARCHAR(32);
ALTER TABLE wms_asn ADD COLUMN cross_dock_qty DECIMAL(18,3);
ALTER TABLE wms_pick_task ADD COLUMN wave_id BIGINT;
ALTER TABLE wms_item ADD COLUMN qc_required BOOLEAN DEFAULT FALSE;
ALTER TABLE wms_asn ADD COLUMN customer_code VARCHAR(32);
ALTER TABLE wms_asn ADD COLUMN qc_qty DECIMAL(18,3);
ALTER TABLE wms_asn ADD COLUMN rejected_qty DECIMAL(18,3);
ALTER TABLE wms_asn_line ADD COLUMN rejected_qty DECIMAL(18,3) DEFAULT 0;
ALTER TABLE wms_ship_order ADD COLUMN tracking_no VARCHAR(64);
ALTER TABLE wms_ship_order ADD COLUMN package_count INT;
ALTER TABLE wms_ship_order ADD COLUMN gross_weight DECIMAL(18,3);
ALTER TABLE wms_ship_order ADD COLUMN packed_at TIMESTAMP;
ALTER TABLE wms_ship_order ADD COLUMN shipped_at TIMESTAMP;
ALTER TABLE wms_ship_order ADD COLUMN carton_code VARCHAR(32);
ALTER TABLE wms_item ADD COLUMN sn_control BOOLEAN DEFAULT FALSE;
ALTER TABLE wms_inventory ADD COLUMN count_lock BOOLEAN DEFAULT FALSE;
ALTER TABLE wms_inventory ADD COLUMN count_plan_id BIGINT;
ALTER TABLE wms_wave ADD COLUMN strategy_id BIGINT;
ALTER TABLE wms_wave ADD COLUMN strategy_code VARCHAR(32);
ALTER TABLE wms_wave ADD COLUMN pack_strategy VARCHAR(32);
ALTER TABLE wms_pick_task ADD COLUMN short_qty DECIMAL(18,3) DEFAULT 0;
ALTER TABLE wms_wave ADD COLUMN max_package_weight DECIMAL(18,3);
ALTER TABLE wms_warehouse ADD COLUMN approval_required BOOLEAN DEFAULT FALSE;
ALTER TABLE wms_asn ADD COLUMN approval_status VARCHAR(16);
ALTER TABLE wms_asn ADD COLUMN approved_by VARCHAR(64);
ALTER TABLE wms_asn ADD COLUMN approved_at TIMESTAMP;
ALTER TABLE wms_asn ADD COLUMN approval_remark VARCHAR(255);
ALTER TABLE wms_ship_order ADD COLUMN approval_status VARCHAR(16);
ALTER TABLE wms_ship_order ADD COLUMN approved_by VARCHAR(64);
ALTER TABLE wms_ship_order ADD COLUMN approved_at TIMESTAMP;
ALTER TABLE wms_ship_order ADD COLUMN approval_remark VARCHAR(255);
-- wms_role 新表由 schema.sql 的 CREATE TABLE IF NOT EXISTS 在启动时创建, 内置角色 ADMIN/OPERATOR/VIEWER 由应用启动时自动补种。
ALTER TABLE wms_warehouse ADD COLUMN profile VARCHAR(16) DEFAULT 'GENERAL';
ALTER TABLE wms_location ADD COLUMN handling VARCHAR(16) DEFAULT 'BIN';
ALTER TABLE wms_location ADD COLUMN pallet_slots INT;
ALTER TABLE wms_item ADD COLUMN handling_class VARCHAR(16) DEFAULT 'EACH';
ALTER TABLE wms_inventory ADD COLUMN lpn_code VARCHAR(64) DEFAULT '';
ALTER TABLE wms_inventory ADD COLUMN ownership VARCHAR(16) DEFAULT 'CUSTOMER';
ALTER TABLE wms_ship_order ADD COLUMN order_shape VARCHAR(16);
ALTER TABLE wms_ship_order ADD COLUMN delivery_window VARCHAR(64);
ALTER TABLE wms_ship_order ADD COLUMN install_required BOOLEAN DEFAULT FALSE;
ALTER TABLE wms_inventory_txn MODIFY txn_type VARCHAR(32) NOT NULL;
CREATE UNIQUE INDEX uk_inv_grain ON wms_inventory (
  warehouse_code, location_code, owner_code, item_code, lot_no, status, ref_no, lpn_code, ownership, expiry_date
);
