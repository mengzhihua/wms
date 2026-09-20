-- ===================== 基础数据 =====================
CREATE TABLE IF NOT EXISTS wms_warehouse (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  address VARCHAR(255),
  contact VARCHAR(64),
  phone VARCHAR(32),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_warehouse_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS wms_zone (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  warehouse_code VARCHAR(32) NOT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  type VARCHAR(32) NOT NULL,
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_zone_code UNIQUE (warehouse_code, code)
);

CREATE TABLE IF NOT EXISTS wms_location (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  warehouse_code VARCHAR(32) NOT NULL,
  zone_code VARCHAR(32) NOT NULL,
  code VARCHAR(32) NOT NULL,
  type VARCHAR(32) NOT NULL,
  abc_class VARCHAR(4),
  aisle VARCHAR(16),
  bay VARCHAR(16),
  level VARCHAR(16),
  max_weight DECIMAL(18,3),
  max_volume DECIMAL(18,3),
  pick_seq INT DEFAULT 0,
  mix_sku BOOLEAN DEFAULT TRUE,
  mix_lot BOOLEAN DEFAULT TRUE,
  status VARCHAR(16) DEFAULT 'AVAILABLE',
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_location_code UNIQUE (warehouse_code, code)
);

CREATE TABLE IF NOT EXISTS wms_owner (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  contact VARCHAR(64),
  phone VARCHAR(32),
  address VARCHAR(255),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_owner_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS wms_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_code VARCHAR(32) NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  spec VARCHAR(128),
  unit VARCHAR(16) DEFAULT 'EA',
  pack_qty DECIMAL(18,3),
  barcode VARCHAR(64),
  category VARCHAR(64),
  lot_control BOOLEAN DEFAULT FALSE,
  shelf_life_days INT,
  abc_class VARCHAR(4),
  weight DECIMAL(18,3),
  volume DECIMAL(18,3),
  min_stock DECIMAL(18,3),
  max_stock DECIMAL(18,3),
  qc_required BOOLEAN DEFAULT FALSE,
  sn_control BOOLEAN DEFAULT FALSE,
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_item_code UNIQUE (owner_code, code)
);

CREATE TABLE IF NOT EXISTS wms_supplier (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  contact VARCHAR(64),
  phone VARCHAR(32),
  address VARCHAR(255),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_supplier_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS wms_customer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  contact VARCHAR(64),
  phone VARCHAR(32),
  address VARCHAR(255),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_customer_code UNIQUE (code)
);

-- ===================== 库存 =====================
CREATE TABLE IF NOT EXISTS wms_inventory (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  warehouse_code VARCHAR(32) NOT NULL,
  location_code VARCHAR(32) NOT NULL,
  owner_code VARCHAR(32) NOT NULL,
  item_code VARCHAR(64) NOT NULL,
  lot_no VARCHAR(64) DEFAULT '',
  qty DECIMAL(18,3) NOT NULL DEFAULT 0,
  allocated_qty DECIMAL(18,3) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
  receive_date DATE,
  expiry_date DATE,
  ref_no VARCHAR(64) DEFAULT '',
  count_lock BOOLEAN DEFAULT FALSE,
  count_plan_id BIGINT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_inv_item (warehouse_code, owner_code, item_code),
  INDEX idx_inv_loc (warehouse_code, location_code)
);

CREATE TABLE IF NOT EXISTS wms_inventory_txn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  txn_type VARCHAR(16) NOT NULL,
  warehouse_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  from_location VARCHAR(32),
  to_location VARCHAR(32),
  qty DECIMAL(18,3),
  ref_no VARCHAR(64),
  operator VARCHAR(64),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wms_count_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  warehouse_code VARCHAR(32) NOT NULL,
  zone_code VARCHAR(32),
  status VARCHAR(16) NOT NULL,
  remark VARCHAR(255),
  line_count INT,
  diff_count INT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wms_count_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  count_id BIGINT NOT NULL,
  inventory_id BIGINT,
  location_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  system_qty DECIMAL(18,3),
  count_qty DECIMAL(18,3),
  diff_qty DECIMAL(18,3),
  status VARCHAR(16),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- ===================== 入库 =====================
CREATE TABLE IF NOT EXISTS wms_asn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  warehouse_code VARCHAR(32) NOT NULL,
  owner_code VARCHAR(32) NOT NULL,
  supplier_code VARCHAR(32),
  type VARCHAR(16) DEFAULT 'PURCHASE',
  status VARCHAR(16) NOT NULL,
  expected_date DATE,
  external_no VARCHAR(64),
  remark VARCHAR(255),
  total_qty DECIMAL(18,3),
  received_qty DECIMAL(18,3),
  putaway_qty DECIMAL(18,3),
  cross_dock_order_code VARCHAR(32),
  cross_dock_qty DECIMAL(18,3),
  customer_code VARCHAR(32),
  qc_qty DECIMAL(18,3),
  rejected_qty DECIMAL(18,3),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_asn_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS wms_asn_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asn_id BIGINT NOT NULL,
  line_no INT,
  item_code VARCHAR(64) NOT NULL,
  lot_no VARCHAR(64),
  expiry_date DATE,
  expected_qty DECIMAL(18,3) NOT NULL,
  received_qty DECIMAL(18,3) DEFAULT 0,
  putaway_qty DECIMAL(18,3) DEFAULT 0,
  rejected_qty DECIMAL(18,3) DEFAULT 0,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wms_putaway_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  asn_id BIGINT NOT NULL,
  asn_line_id BIGINT NOT NULL,
  asn_code VARCHAR(32),
  warehouse_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  inventory_id BIGINT,
  from_location VARCHAR(32),
  suggest_location VARCHAR(32),
  to_location VARCHAR(32),
  qty DECIMAL(18,3),
  status VARCHAR(16),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- ===================== 出库 =====================
CREATE TABLE IF NOT EXISTS wms_ship_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  warehouse_code VARCHAR(32) NOT NULL,
  owner_code VARCHAR(32) NOT NULL,
  customer_code VARCHAR(32),
  type VARCHAR(16) DEFAULT 'SALES',
  status VARCHAR(16) NOT NULL,
  priority INT DEFAULT 5,
  expected_ship_date DATE,
  external_no VARCHAR(64),
  carrier VARCHAR(64),
  address VARCHAR(255),
  remark VARCHAR(255),
  total_qty DECIMAL(18,3),
  allocated_qty DECIMAL(18,3),
  picked_qty DECIMAL(18,3),
  shipped_qty DECIMAL(18,3),
  tracking_no VARCHAR(64),
  carton_code VARCHAR(32),
  package_count INT,
  gross_weight DECIMAL(18,3),
  packed_at TIMESTAMP,
  shipped_at TIMESTAMP,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_so_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS wms_ship_order_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  line_no INT,
  item_code VARCHAR(64) NOT NULL,
  lot_no VARCHAR(64),
  order_qty DECIMAL(18,3) NOT NULL,
  allocated_qty DECIMAL(18,3) DEFAULT 0,
  picked_qty DECIMAL(18,3) DEFAULT 0,
  shipped_qty DECIMAL(18,3) DEFAULT 0,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wms_pick_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  order_id BIGINT NOT NULL,
  order_line_id BIGINT NOT NULL,
  order_code VARCHAR(32),
  warehouse_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  inventory_id BIGINT,
  from_location VARCHAR(32),
  to_location VARCHAR(32),
  qty DECIMAL(18,3),
  picked_qty DECIMAL(18,3),
  short_qty DECIMAL(18,3) DEFAULT 0,
  status VARCHAR(16),
  wave_id BIGINT,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- 波次 (Wave): 多张出库单合并成一次总拣 + 播种(按单分拨)
CREATE TABLE IF NOT EXISTS wms_wave (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  warehouse_code VARCHAR(32) NOT NULL,
  status VARCHAR(16) NOT NULL,
  order_count INT,
  total_qty DECIMAL(18,3),
  picked_qty DECIMAL(18,3),
  sowed_qty DECIMAL(18,3),
  remark VARCHAR(255),
  strategy_id BIGINT,
  strategy_code VARCHAR(32),
  pack_strategy VARCHAR(32),
  max_package_weight DECIMAL(18,3),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_wave_code UNIQUE (code)
);

-- 波次总拣任务: 同一库存记录(库位/物料/批次)上多张单的拣货任务合并
CREATE TABLE IF NOT EXISTS wms_wave_pick_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  wave_id BIGINT NOT NULL,
  wave_code VARCHAR(32),
  warehouse_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  inventory_id BIGINT,
  from_location VARCHAR(32),
  to_location VARCHAR(32),
  qty DECIMAL(18,3),
  picked_qty DECIMAL(18,3),
  order_count INT,
  status VARCHAR(16),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- 播种任务: 总拣完成后按出库单/物料分拨到播种位
CREATE TABLE IF NOT EXISTS wms_sow_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  wave_id BIGINT NOT NULL,
  wave_code VARCHAR(32),
  order_id BIGINT NOT NULL,
  order_code VARCHAR(32),
  slot_no INT,
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  qty DECIMAL(18,3),
  sowed_qty DECIMAL(18,3),
  status VARCHAR(16),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- 系统用户（首次启动无用户时由 UserService 创建 admin）
CREATE TABLE IF NOT EXISTS wms_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  real_name VARCHAR(64),
  role VARCHAR(16) NOT NULL,
  status INT DEFAULT 1,
  last_login_at TIMESTAMP,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_user_name UNIQUE (username)
);

-- ===================== 单号序列 / 操作日志 =====================
CREATE TABLE IF NOT EXISTS wms_sequence (
  prefix VARCHAR(16) NOT NULL,
  day_key VARCHAR(8) NOT NULL,
  seq_value INT NOT NULL,
  PRIMARY KEY (prefix, day_key)
);

CREATE TABLE IF NOT EXISTS wms_op_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64),
  method VARCHAR(8),
  path VARCHAR(255),
  query VARCHAR(255),
  http_status INT,
  cost_ms INT,
  client_ip VARCHAR(64),
  created_at TIMESTAMP
);
CREATE INDEX idx_op_log_created ON wms_op_log (created_at);

-- ===================== 包材/箱型 =====================
CREATE TABLE IF NOT EXISTS wms_carton (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  length_cm DECIMAL(10,2),
  width_cm DECIMAL(10,2),
  height_cm DECIMAL(10,2),
  volume DECIMAL(18,6),
  max_weight DECIMAL(18,3),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  status INT DEFAULT 1,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_carton_code UNIQUE (code)
);

-- ===================== 序列号(SN) =====================
CREATE TABLE IF NOT EXISTS wms_serial (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_code VARCHAR(32) NOT NULL,
  item_code VARCHAR(64) NOT NULL,
  serial_no VARCHAR(64) NOT NULL,
  lot_no VARCHAR(64),
  status VARCHAR(16),
  asn_code VARCHAR(32),
  order_code VARCHAR(32),
  location_code VARCHAR(32),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_serial UNIQUE (owner_code, serial_no)
);

-- ===================== 质检 =====================
CREATE TABLE IF NOT EXISTS wms_qc_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  asn_id BIGINT NOT NULL,
  asn_line_id BIGINT NOT NULL,
  asn_code VARCHAR(32),
  warehouse_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  inventory_id BIGINT,
  location_code VARCHAR(32),
  qty DECIMAL(18,3),
  pass_qty DECIMAL(18,3),
  reject_qty DECIMAL(18,3),
  reject_reason VARCHAR(255),
  inspector VARCHAR(64),
  status VARCHAR(16),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- ===================== 补货 =====================
CREATE TABLE IF NOT EXISTS wms_replenish_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  warehouse_code VARCHAR(32) NOT NULL,
  owner_code VARCHAR(32) NOT NULL,
  item_code VARCHAR(64) NOT NULL,
  lot_no VARCHAR(64),
  inventory_id BIGINT,
  from_location VARCHAR(32),
  to_location VARCHAR(32),
  qty DECIMAL(18,3),
  status VARCHAR(16),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- ===================== 盘点计划体系 =====================
-- 盘点计划: DRAFT -> PENDING -> APPROVED -> EXECUTING -> COMPLETED / CANCELLED
CREATE TABLE IF NOT EXISTS wms_count_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128),
  warehouse_code VARCHAR(32) NOT NULL,
  type VARCHAR(16) NOT NULL,
  scope_type VARCHAR(16) NOT NULL,
  zone_code VARCHAR(32),
  item_codes VARCHAR(1024),
  abc_classes VARCHAR(16),
  since_date DATE,
  sample_percent INT,
  status VARCHAR(16) NOT NULL,
  approver VARCHAR(64),
  approve_opinion VARCHAR(255),
  task_count INT DEFAULT 0,
  done_count INT DEFAULT 0,
  diff_count INT DEFAULT 0,
  remark VARCHAR(255),
  created_by VARCHAR(64),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_count_plan_code UNIQUE (code)
);

-- 盘点任务: 一条库存一条任务 PENDING(待领取) -> CLAIMED -> DONE / CANCELLED
CREATE TABLE IF NOT EXISTS wms_count_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  plan_code VARCHAR(32),
  inventory_id BIGINT NOT NULL,
  warehouse_code VARCHAR(32),
  location_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  system_qty DECIMAL(18,3),
  count_qty DECIMAL(18,3),
  diff_qty DECIMAL(18,3),
  assignee VARCHAR(64),
  status VARCHAR(16) NOT NULL,
  counted_at TIMESTAMP,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_count_task_plan (plan_id)
);

-- 复盘任务(轮次): PENDING -> RECOUNTED -> CONFIRMED
CREATE TABLE IF NOT EXISTS wms_recount_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  round_no INT NOT NULL,
  count_task_id BIGINT NOT NULL,
  inventory_id BIGINT NOT NULL,
  location_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  system_qty DECIMAL(18,3),
  first_qty DECIMAL(18,3),
  recount_qty DECIMAL(18,3),
  recount_diff DECIMAL(18,3),
  final_qty DECIMAL(18,3),
  final_diff DECIMAL(18,3),
  final_result VARCHAR(16),
  assignee VARCHAR(64),
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_recount_plan (plan_id)
);

-- 库存调整单: PENDING -> APPROVED / REJECTED
CREATE TABLE IF NOT EXISTS wms_stock_adjust (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  plan_id BIGINT,
  plan_code VARCHAR(32),
  warehouse_code VARCHAR(32) NOT NULL,
  source VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  line_count INT DEFAULT 0,
  gain_qty DECIMAL(18,3) DEFAULT 0,
  loss_qty DECIMAL(18,3) DEFAULT 0,
  approver VARCHAR(64),
  approve_opinion VARCHAR(255),
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_stock_adjust_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS wms_stock_adjust_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  adjust_id BIGINT NOT NULL,
  inventory_id BIGINT NOT NULL,
  location_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  from_qty DECIMAL(18,3),
  to_qty DECIMAL(18,3),
  diff_qty DECIMAL(18,3),
  reason VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_adjust_line (adjust_id)
);

-- ===================== 波次策略 / 缺货 / 包裹 =====================
CREATE TABLE IF NOT EXISTS wms_wave_strategy (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  priority INT NOT NULL DEFAULT 100,
  min_orders INT DEFAULT 1,
  max_orders INT DEFAULT 100,
  min_sku_per_order INT DEFAULT 0,
  max_sku_per_order INT DEFAULT 0,
  min_qty_per_order DECIMAL(18,3) DEFAULT 0,
  max_qty_per_order DECIMAL(18,3) DEFAULT 0,
  max_sku_items INT DEFAULT 0,
  max_total_qty DECIMAL(18,3) DEFAULT 0,
  group_by_item BOOLEAN DEFAULT FALSE,
  group_by_owner BOOLEAN DEFAULT FALSE,
  group_by_carrier BOOLEAN DEFAULT FALSE,
  zone_code VARCHAR(32),
  cutoff_hour INT,
  pack_strategy VARCHAR(32) DEFAULT 'ONE_ORDER_ONE_PACKAGE',
  max_package_weight DECIMAL(18,3),
  enabled BOOLEAN DEFAULT TRUE,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_wave_strategy_code UNIQUE (code)
);

-- 缺货登记
CREATE TABLE IF NOT EXISTS wms_shortage (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  order_code VARCHAR(32),
  wave_id BIGINT,
  warehouse_code VARCHAR(32),
  owner_code VARCHAR(32),
  item_code VARCHAR(64),
  lot_no VARCHAR(64),
  location_code VARCHAR(32),
  inventory_id BIGINT,
  qty DECIMAL(18,3) NOT NULL,
  reason VARCHAR(255),
  operator VARCHAR(64),
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP,
  updated_at TIMESTAMP
);

-- 包裹: NEW -> PACKED -> SHIPPED / CANCELLED
CREATE TABLE IF NOT EXISTS wms_package (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  order_id BIGINT NOT NULL,
  order_code VARCHAR(32),
  wave_id BIGINT,
  seq_no INT,
  type VARCHAR(32),
  carton_code VARCHAR(32),
  weight DECIMAL(18,3),
  carrier VARCHAR(64),
  tracking_no VARCHAR(64),
  status VARCHAR(16) NOT NULL,
  remark VARCHAR(255),
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  CONSTRAINT uk_package_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS wms_package_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  package_id BIGINT NOT NULL,
  item_code VARCHAR(64) NOT NULL,
  lot_no VARCHAR(64),
  qty DECIMAL(18,3) NOT NULL,
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  INDEX idx_package_line (package_id)
);
