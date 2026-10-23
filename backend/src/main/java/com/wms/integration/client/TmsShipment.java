package com.wms.integration.client;

import com.wms.outbound.entity.ShipOrder;

import java.util.LinkedHashMap;
import java.util.Map;

/** 送装预约交给 TMS。WMS 只传窗口和是否安装，不排车。 */
public final class TmsShipment {
    private TmsShipment() {
    }

    public static Map<String, Object> payload(ShipOrder order) {
        if (order == null) {
            return null;
        }
        boolean install = Boolean.TRUE.equals(order.getInstallRequired());
        String window = order.getDeliveryWindow();
        boolean hasWindow = window != null && !window.trim().isEmpty();
        if (!install && !hasWindow) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("wmsOrderCode", order.getCode());
        body.put("externalNo", order.getExternalNo());
        body.put("warehouseCode", order.getWarehouseCode());
        body.put("carrier", order.getCarrier());
        body.put("trackingNo", order.getTrackingNo());
        body.put("address", order.getAddress());
        body.put("deliveryWindow", hasWindow ? window.trim() : null);
        body.put("installRequired", install);
        return body;
    }
}
