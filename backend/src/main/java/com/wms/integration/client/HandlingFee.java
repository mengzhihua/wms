package com.wms.integration.client;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 出库操作费。仓号保持 WMS 编码，BMS 入库时再映到控制塔仓号。 */
public final class HandlingFee {
    private HandlingFee() {
    }

    public static Map<String, Object> doc(String orderNo, String warehouseCode, BigDecimal qty) {
        BigDecimal pieces = qty == null || qty.signum() <= 0 ? BigDecimal.ONE : qty;
        Map<String, Object> doc = new LinkedHashMap<String, Object>();
        doc.put("extRef", orderNo + ":HANDLING");
        doc.put("bizType", "OUTBOUND");
        doc.put("customerCode", "CUST-001");
        doc.put("warehouseCode", warehouseCode);
        doc.put("statedAmount", pieces);
        doc.put("direction", "AR");
        doc.put("chargeItemCode", "OUTBOUND_HANDLING");
        doc.put("qty", pieces);
        doc.put("remark", "出库操作费 " + orderNo);
        return doc;
    }
}
