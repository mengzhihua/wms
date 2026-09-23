-- 演示数据（幂等：已存在则跳过）
INSERT INTO wms_warehouse (code, name, address, contact, phone, status, created_at, updated_at)
SELECT 'WH01', '上海中心仓', '上海市青浦区华新镇', '张伟', '13800000001', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_warehouse WHERE code = 'WH01');
INSERT INTO wms_warehouse (code, name, address, contact, phone, status, created_at, updated_at)
SELECT 'WH02', '北京仓', '北京市大兴区', '李娜', '13800000002', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_warehouse WHERE code = 'WH02');
INSERT INTO wms_zone (warehouse_code, code, name, type, status, created_at, updated_at)
SELECT 'WH02', 'PCK', '拣货区', 'PICKING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_zone WHERE warehouse_code='WH02' AND code='PCK');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH02', 'PCK', 'P-01-01', 'PICKING', 'A', '01', '01', '01', 5, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH02' AND code='P-01-01');
INSERT INTO wms_zone (warehouse_code, code, name, type, status, created_at, updated_at)
SELECT 'WH02', 'RCV', '收货区', 'RECEIVING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_zone WHERE warehouse_code='WH02' AND code='RCV');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH02', 'RCV', 'RCV-01', 'STAGING_IN', NULL, NULL, NULL, NULL, 0, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH02' AND code='RCV-01');

INSERT INTO wms_zone (warehouse_code, code, name, type, status, created_at, updated_at)
SELECT 'WH01', 'RCV', '收货区', 'RECEIVING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_zone WHERE warehouse_code='WH01' AND code='RCV');
INSERT INTO wms_zone (warehouse_code, code, name, type, status, created_at, updated_at)
SELECT 'WH01', 'STA', '存储区A', 'STORAGE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_zone WHERE warehouse_code='WH01' AND code='STA');
INSERT INTO wms_zone (warehouse_code, code, name, type, status, created_at, updated_at)
SELECT 'WH01', 'PCK', '拣货区', 'PICKING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_zone WHERE warehouse_code='WH01' AND code='PCK');
INSERT INTO wms_zone (warehouse_code, code, name, type, status, created_at, updated_at)
SELECT 'WH01', 'SHP', '发货区', 'SHIPPING', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_zone WHERE warehouse_code='WH01' AND code='SHP');
INSERT INTO wms_zone (warehouse_code, code, name, type, status, created_at, updated_at)
SELECT 'WH01', 'QC', '质检区', 'QC', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_zone WHERE warehouse_code='WH01' AND code='QC');

INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'RCV', 'RCV-01', 'STAGING_IN', NULL, NULL, NULL, NULL, 0, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='RCV-01');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'SHP', 'SHP-01', 'STAGING_OUT', NULL, NULL, NULL, NULL, 0, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='SHP-01');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'QC', 'QC-01', 'QC', NULL, NULL, NULL, NULL, 0, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='QC-01');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'STA', 'A-01-01-01', 'STORAGE', 'A', '01', '01', '01', 10, FALSE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='A-01-01-01');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'STA', 'A-01-01-02', 'STORAGE', 'A', '01', '01', '02', 20, FALSE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='A-01-01-02');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'STA', 'A-01-02-01', 'STORAGE', 'A', '01', '02', '01', 30, FALSE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='A-01-02-01');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'STA', 'A-01-02-02', 'STORAGE', 'B', '01', '02', '02', 40, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='A-01-02-02');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'STA', 'A-02-01-01', 'STORAGE', 'B', '02', '01', '01', 50, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='A-02-01-01');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'STA', 'A-02-01-02', 'STORAGE', 'C', '02', '01', '02', 60, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='A-02-01-02');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'PCK', 'P-01-01', 'PICKING', 'A', '01', '01', '01', 5, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='P-01-01');
INSERT INTO wms_location (warehouse_code, zone_code, code, type, abc_class, aisle, bay, level, pick_seq, mix_sku, mix_lot, status, created_at, updated_at)
SELECT 'WH01', 'PCK', 'P-01-02', 'PICKING', 'A', '01', '02', '01', 6, TRUE, TRUE, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_location WHERE warehouse_code='WH01' AND code='P-01-02');

INSERT INTO wms_owner (code, name, contact, phone, address, status, created_at, updated_at)
SELECT 'OWN01', '华东电子有限公司', '李娜', '13900000001', '上海市浦东新区', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_owner WHERE code='OWN01');

INSERT INTO wms_item (owner_code, code, name, spec, unit, pack_qty, barcode, category, lot_control, shelf_life_days, abc_class, weight, volume, min_stock, max_stock, status, created_at, updated_at)
SELECT 'OWN01', 'SKU001', '无线鼠标 M1', '黑色', 'EA', 20, '6901234567890', '电脑配件', TRUE, 730, 'A', 0.12, 0.0005, 50, 2000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_item WHERE owner_code='OWN01' AND code='SKU001');
INSERT INTO wms_item (owner_code, code, name, spec, unit, pack_qty, barcode, category, lot_control, shelf_life_days, abc_class, weight, volume, min_stock, max_stock, status, created_at, updated_at)
SELECT 'OWN01', 'SKU002', '机械键盘 K87', '白色 87键', 'EA', 10, '6901234567891', '电脑配件', TRUE, 730, 'A', 0.85, 0.003, 30, 1000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_item WHERE owner_code='OWN01' AND code='SKU002');
INSERT INTO wms_item (owner_code, code, name, spec, unit, pack_qty, barcode, category, lot_control, shelf_life_days, abc_class, weight, volume, min_stock, max_stock, status, created_at, updated_at)
SELECT 'OWN01', 'SKU003', 'USB-C 数据线 1m', '1米', 'EA', 100, '6901234567892', '线材', FALSE, NULL, 'B', 0.03, 0.0001, 200, 5000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_item WHERE owner_code='OWN01' AND code='SKU003');
INSERT INTO wms_item (owner_code, code, name, spec, unit, pack_qty, barcode, category, lot_control, shelf_life_days, abc_class, weight, volume, min_stock, max_stock, qc_required, status, created_at, updated_at)
SELECT 'OWN01', 'SKU004', '27寸显示器', '2K 144Hz', 'EA', 1, '6901234567893', '显示设备', FALSE, NULL, 'C', 5.5, 0.05, 5, 200, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_item WHERE owner_code='OWN01' AND code='SKU004');

INSERT INTO wms_item (owner_code, code, name, spec, unit, pack_qty, barcode, category, lot_control, shelf_life_days, abc_class, weight, volume, min_stock, max_stock, sn_control, status, created_at, updated_at)
SELECT 'OWN01', 'SKU005', '智能手机 X1', '256G 黑', 'EA', 1, '6901234567894', '手机', FALSE, NULL, 'A', 0.25, 0.0008, 10, 500, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_item WHERE owner_code='OWN01' AND code='SKU005');
INSERT INTO wms_item (owner_code, code, name, spec, unit, pack_qty, barcode, category, lot_control, shelf_life_days, abc_class, weight, volume, min_stock, max_stock, status, created_at, updated_at)
SELECT 'OWN01', 'PKG-BOX-M', '中号纸箱(包材)', '40x30x25', 'EA', 50, NULL, '包材', FALSE, NULL, 'C', 0.3, 0.03, 100, 2000, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_item WHERE owner_code='OWN01' AND code='PKG-BOX-M');

INSERT INTO wms_supplier (code, name, contact, phone, address, status, created_at, updated_at)
SELECT 'SUP01', '深圳精密电子厂', '王强', '13700000001', '深圳市宝安区', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_supplier WHERE code='SUP01');
INSERT INTO wms_supplier (code, name, contact, phone, address, status, created_at, updated_at)
SELECT 'SUP02', '东莞线材制造', '赵敏', '13700000002', '东莞市松山湖', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_supplier WHERE code='SUP02');

INSERT INTO wms_customer (code, name, contact, phone, address, status, created_at, updated_at)
SELECT 'CUS01', '京东华东仓', '刘洋', '13600000001', '江苏省昆山市', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_customer WHERE code='CUS01');
INSERT INTO wms_customer (code, name, contact, phone, address, status, created_at, updated_at)
SELECT 'CUS02', '苏宁易购南京店', '陈静', '13600000002', '江苏省南京市', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_customer WHERE code='CUS02');

-- 包材/箱型（体积 m³ = 长*宽*高 / 1e6）
INSERT INTO wms_carton (code, name, length_cm, width_cm, height_cm, volume, max_weight, status, created_at, updated_at)
SELECT 'BOX-S', '小号纸箱', 20, 15, 10, 0.003, 5, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_carton WHERE code='BOX-S');
INSERT INTO wms_carton (code, name, length_cm, width_cm, height_cm, volume, max_weight, owner_code, item_code, status, created_at, updated_at)
SELECT 'BOX-M', '中号纸箱', 40, 30, 25, 0.03, 15, 'OWN01', 'PKG-BOX-M', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_carton WHERE code='BOX-M');
INSERT INTO wms_carton (code, name, length_cm, width_cm, height_cm, volume, max_weight, status, created_at, updated_at)
SELECT 'BOX-L', '大号纸箱', 60, 40, 40, 0.096, 30, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_carton WHERE code='BOX-L');

-- IR HTTP 联调：库存放在存储位，避免占满拣货位导致 Min/Max 补货单测不触发
INSERT INTO wms_inventory (warehouse_code, location_code, owner_code, item_code, lot_no, qty, allocated_qty, status, created_at, updated_at)
SELECT 'WH01', 'A-01-01-01', 'OWN01', 'SKU001', 'LOT-IR', 200, 0, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory WHERE warehouse_code='WH01' AND location_code='A-01-01-01' AND item_code='SKU001' AND lot_no='LOT-IR');
INSERT INTO wms_inventory (warehouse_code, location_code, owner_code, item_code, lot_no, qty, allocated_qty, status, created_at, updated_at)
SELECT 'WH01', 'A-01-01-02', 'OWN01', 'SKU003', 'LOT-XFER', 20, 0, 'AVAILABLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_inventory WHERE warehouse_code='WH01' AND location_code='A-01-01-02' AND item_code='SKU003' AND lot_no='LOT-XFER');

INSERT INTO wms_ship_order (code, warehouse_code, owner_code, customer_code, type, status, priority, expected_ship_date,
    external_no, carrier, address, total_qty, allocated_qty, picked_qty, shipped_qty, created_at, updated_at)
SELECT 'SO-IR-STUCK', 'WH01', 'OWN01', 'CUS01', 'SALES', 'NEW', 10, CURRENT_DATE,
    'IR-SO-STUCK', 'SF', 'IR 演示出库卡单', 2, 0, 0, 0,
    TIMESTAMPADD(HOUR, -10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM wms_ship_order WHERE code='SO-IR-STUCK');

INSERT INTO wms_ship_order_line (order_id, line_no, item_code, order_qty, allocated_qty, picked_qty, shipped_qty, created_at, updated_at)
SELECT id, 1, 'SKU001', 2, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM wms_ship_order WHERE code='SO-IR-STUCK'
  AND NOT EXISTS (SELECT 1 FROM wms_ship_order_line WHERE order_id=wms_ship_order.id);

-- 波次策略预置(按优先级依次匹配已分配出库单; 打包策略: ONE_ORDER_ONE_PACKAGE 一单一包 / SPLIT_BY_WEIGHT 按重量拆包)
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, min_sku_per_order, max_sku_per_order, min_qty_per_order, max_qty_per_order, group_by_item, pack_strategy, enabled, remark, created_at, updated_at)
SELECT 'SISQ', '单品单件', 10, 2, 50, 1, 1, 1, 1, TRUE, 'ONE_ORDER_ONE_PACKAGE', TRUE, '每单一个SKU一件, 同SKU成波', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='SISQ');
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, min_sku_per_order, max_sku_per_order, min_qty_per_order, max_qty_per_order, group_by_item, pack_strategy, enabled, remark, created_at, updated_at)
SELECT 'SIFQ_2', '一品两件', 20, 2, 50, 1, 1, 2, 2, TRUE, 'ONE_ORDER_ONE_PACKAGE', TRUE, '每单一个SKU两件, 同SKU成波', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='SIFQ_2');
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, min_sku_per_order, max_sku_per_order, min_qty_per_order, max_qty_per_order, group_by_item, pack_strategy, max_package_weight, enabled, remark, created_at, updated_at)
SELECT 'SIW', '一品波次', 30, 2, 50, 1, 1, 0, 0, TRUE, 'SPLIT_BY_WEIGHT', 15, TRUE, '每单一个SKU件数不限, 同SKU成波', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='SIW');
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, min_sku_per_order, max_sku_per_order, min_qty_per_order, max_qty_per_order, group_by_item, pack_strategy, enabled, remark, created_at, updated_at)
SELECT 'TCQ_2', '两品两件(A+B)', 40, 2, 50, 2, 2, 2, 2, FALSE, 'ONE_ORDER_ONE_PACKAGE', TRUE, '每单两个SKU共两件', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='TCQ_2');
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, min_sku_per_order, max_sku_per_order, min_qty_per_order, max_qty_per_order, group_by_item, pack_strategy, enabled, remark, created_at, updated_at)
SELECT 'SIMO', '一单一件多品波次', 50, 2, 100, 1, 1, 1, 1, FALSE, 'ONE_ORDER_ONE_PACKAGE', TRUE, '每单一件, 波次内SKU可不同', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='SIMO');
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, zone_code, pack_strategy, max_package_weight, enabled, remark, created_at, updated_at)
SELECT 'SAW', '单区波次', 60, 1, 30, 'STA', 'SPLIT_BY_WEIGHT', 15, TRUE, '出库单库存全部位于同一储区', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='SAW');
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, pack_strategy, max_package_weight, enabled, remark, created_at, updated_at)
SELECT 'MIX', '混合(默认)', 90, 1, 30, 'SPLIT_BY_WEIGHT', 15, TRUE, '无约束, 兜底策略', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='MIX');
INSERT INTO wms_wave_strategy (code, name, priority, min_orders, max_orders, cutoff_hour, pack_strategy, max_package_weight, enabled, remark, created_at, updated_at)
SELECT 'EOW', '尾单', 99, 1, 200, 17, 'SPLIT_BY_WEIGHT', 15, FALSE, '每天 17 点后把剩余订单收尾成波', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wms_wave_strategy WHERE code='EOW');
