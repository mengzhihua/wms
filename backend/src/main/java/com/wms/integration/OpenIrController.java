package com.wms.integration;

import com.wms.common.BizException;
import com.wms.common.R;
import com.wms.inventory.entity.ReplenishTask;
import com.wms.inventory.service.ReplenishService;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.service.ShipOrderService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** IR 控制塔开放指令：按出库单号/外部单号分配，按仓触发补货。 */
@RestController
@RequestMapping("/api/open/ir")
@RequiredArgsConstructor
public class OpenIrController {
    private final ShipOrderService shipOrderService;
    private final ReplenishService replenishService;

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
        private String targetKey;
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
        return R.ok(replenishService.generate(toWmsWarehouse(warehouse), req.getOwnerCode()));
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
                    string(params.get("ownerCode"))));
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
}
