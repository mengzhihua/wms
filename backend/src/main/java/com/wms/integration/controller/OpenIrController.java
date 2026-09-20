package com.wms.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.basic.entity.Item;
import com.wms.basic.mapper.ItemMapper;
import com.wms.common.BizException;
import com.wms.common.R;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.service.ReplenishService;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.mapper.ShipOrderLineMapper;
import com.wms.outbound.mapper.ShipOrderMapper;
import com.wms.outbound.service.ShipOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** IR 控制塔开放指令：按出库单号/外部单号分配，按仓/SKU 触发补货。 */
@RestController
@RequestMapping("/api/open/ir")
@RequiredArgsConstructor
public class OpenIrController {
    private final ShipOrderService shipOrderService;
    private final ReplenishService replenishService;
    private final ShipOrderMapper shipOrderMapper;
    private final ShipOrderLineMapper shipOrderLineMapper;
    private final InventoryMapper inventoryMapper;
    private final ItemMapper itemMapper;

    @Data
    public static class AllocateReq {
        private String orderCode;
        private String externalNo;
        private String targetKey;
    }

    @Data
    public static class ReplenishReq {
        private String warehouseCode;
        private String ownerCode;
        private String sku;
        private String targetKey;
    }

    @GetMapping("/snapshots")
    public R<Map<String, Object>> snapshots() {
        Map<Long, String> skuByOrderId = firstSkuByOrder();
        List<Map<String, Object>> outbound = new ArrayList<Map<String, Object>>();
        for (ShipOrder order : shipOrderMapper.selectList(null)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            String sku = skuByOrderId.get(order.getId());
            row.put("code", order.getCode());
            row.put("orderNo", order.getCode());
            row.put("orderCode", order.getCode());
            row.put("externalNo", order.getExternalNo());
            row.put("sourceNo", order.getExternalNo());
            row.put("warehouseCode", order.getWarehouseCode());
            row.put("status", order.getStatus());
            row.put("sku", sku);
            row.put("skuCode", sku);
            row.put("itemCode", sku);
            row.put("totalQty", order.getTotalQty());
            row.put("qty", order.getTotalQty());
            row.put("pickedQty", order.getPickedQty());
            row.put("shippedQty", order.getShippedQty());
            row.put("carrier", order.getCarrier());
            row.put("carrierCode", order.getCarrier());
            row.put("trackingNo", order.getTrackingNo());
            row.put("packedAt", order.getPackedAt());
            row.put("shippedAt", order.getShippedAt());
            outbound.add(row);
        }
        Map<String, BigDecimal> minStock = minStockByItem();
        List<Map<String, Object>> inventory = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> summary : inventoryMapper.summaryByItem()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            Object sku = mapGet(summary, "itemCode", "sku");
            Object qty = mapGet(summary, "qty", "qtyOnHand");
            Object reserved = mapGet(summary, "allocatedQty", "qtyReserved");
            Object available = mapGet(summary, "availableQty", "qtyAvailable");
            BigDecimal safety = sku == null ? BigDecimal.ZERO
                    : minStock.getOrDefault(String.valueOf(sku), BigDecimal.ZERO);
            row.put("warehouseCode", mapGet(summary, "warehouseCode"));
            row.put("sku", sku);
            row.put("skuCode", sku);
            row.put("itemCode", sku);
            row.put("qtyOnHand", qty);
            row.put("quantity", qty);
            row.put("qtyReserved", reserved);
            row.put("reserved", reserved);
            row.put("qtyAvailable", available);
            row.put("available", available);
            row.put("safetyQty", safety);
            row.put("minStock", safety);
            inventory.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("system", "WMS");
        payload.put("outbound", outbound);
        payload.put("inventory", inventory);
        return R.ok(payload);
    }

    @PostMapping("/allocate")
    public R<ShipOrder> allocate(@RequestBody AllocateReq req) {
        String key = first(req.getOrderCode(), req.getExternalNo(), req.getTargetKey());
        if (key == null) {
            throw new BizException("orderCode / externalNo 必填");
        }
        return R.ok(shipOrderService.allocateByKey(key));
    }

    @PostMapping("/replenish")
    public R<List<ReplenishTask>> replenish(@RequestBody ReplenishReq req) {
        String warehouse = first(req.getWarehouseCode(), req.getTargetKey());
        if (warehouse == null) {
            throw new BizException("warehouseCode 必填");
        }
        return R.ok(replenishService.generate(toWmsWarehouse(warehouse), req.getOwnerCode(), req.getSku()));
    }

    @PostMapping("/actions")
    public R<Object> actions(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", ""));
        String targetKey = String.valueOf(body.getOrDefault("targetKey", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params") : new LinkedHashMap<String, Object>();
        if ("WMS_ALLOCATE".equals(type)) {
            return R.ok(shipOrderService.allocateByKey(first(
                    string(params.get("orderCode")), targetKey)));
        }
        if ("WMS_REPLENISH".equals(type)) {
            String warehouse = first(string(params.get("warehouseCode")), targetKey);
            return R.ok(replenishService.generate(toWmsWarehouse(warehouse),
                    string(params.get("ownerCode")),
                    first(string(params.get("sku")), string(params.get("itemCode")))));
        }
        throw new BizException("不支持的 IR 指令: " + type);
    }

    static String toWmsWarehouse(String code) {
        if (code == null) {
            return null;
        }
        if ("WH-SH".equals(code)) {
            return "WH01";
        }
        if ("WH-BJ".equals(code)) {
            return "WH02";
        }
        if ("WH-GZ".equals(code)) {
            return "WH03";
        }
        return code;
    }

    private Map<Long, String> firstSkuByOrder() {
        Map<Long, String> skuByOrderId = new LinkedHashMap<Long, String>();
        List<ShipOrderLine> lines = shipOrderLineMapper.selectList(new LambdaQueryWrapper<ShipOrderLine>()
                .orderByAsc(ShipOrderLine::getOrderId).orderByAsc(ShipOrderLine::getLineNo));
        for (ShipOrderLine line : lines) {
            if (line.getOrderId() == null || skuByOrderId.containsKey(line.getOrderId())) {
                continue;
            }
            if (line.getItemCode() != null && !line.getItemCode().trim().isEmpty()) {
                skuByOrderId.put(line.getOrderId(), line.getItemCode().trim());
            }
        }
        return skuByOrderId;
    }

    private Map<String, BigDecimal> minStockByItem() {
        Map<String, BigDecimal> minStock = new HashMap<String, BigDecimal>();
        for (Item item : itemMapper.selectList(null)) {
            if (item.getCode() == null) {
                continue;
            }
            minStock.put(item.getCode(), item.getMinStock() == null ? BigDecimal.ZERO : item.getMinStock());
        }
        return minStock;
    }

    private static Object first(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).trim().isEmpty()
                    && !"null".equals(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Object mapGet(Map<String, Object> map, String... names) {
        if (map == null) {
            return null;
        }
        for (String name : names) {
            Object hit = first(map.get(name));
            if (hit != null) {
                return hit;
            }
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    Object value = first(entry.getValue());
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}
