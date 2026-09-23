package com.wms.integration.controller;

import com.wms.common.BizException;
import com.wms.common.R;
import com.wms.integration.client.BmsHandlingClient;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.inbound.service.AsnService;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import com.wms.outbound.service.ShipOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OMS 关闭模拟后推送的出库、取消和退货。鉴权沿用开放口 X-Api-Key。
 * 同一外部单号重复推送返回已有单据。
 */
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class OpenFulfillmentController {
    private final ShipOrderService shipOrders;
    private final AsnService asns;
    private final BmsHandlingClient bmsHandling;

    @PostMapping("/outbound")
    public R<ShipOrder> outbound(@RequestBody Map<String, Object> body) {
        String orderNo = text(body.get("orderNo"));
        if (orderNo == null) {
            throw new BizException("orderNo 不能为空");
        }
        ShipOrder existing = shipOrders.findByCodeOrExternal(orderNo);
        if (existing != null && orderNo.equals(existing.getExternalNo())) {
            return R.ok(shipOrders.load(existing.getId()));
        }
        ShipOrder order = new ShipOrder();
        order.setWarehouseCode(warehouse(text(body.get("warehouseCode"))));
        order.setOwnerCode(first(text(body.get("ownerCode")), "OWN01"));
        order.setCustomerCode(first(text(body.get("customerCode")), "OMS"));
        order.setType("SALES");
        order.setExternalNo(orderNo);
        order.setCarrier(text(body.get("carrierCode")));
        order.setAddress(text(body.get("address")));
        order.setRemark("OMS " + orderNo);
        order.setLines(shipLines(body.get("items")));
        ShipOrder created = shipOrders.create(order);
        bmsHandling.push(order);
        return R.ok(created);
    }

    @PostMapping("/outbound/cancel")
    public R<ShipOrder> cancelOutbound(@RequestBody Map<String, Object> body) {
        String orderNo = text(body.get("orderNo"));
        String wmsNo = text(body.get("wmsOrderNo"));
        ShipOrder order = wmsNo == null ? null : shipOrders.findByCodeOrExternal(wmsNo);
        if (order == null && orderNo != null) {
            order = shipOrders.findByCodeOrExternal(orderNo);
        }
        if (order == null) {
            throw new BizException("出库单不存在");
        }
        if (!"CANCELLED".equals(order.getStatus())) {
            shipOrders.cancel(order.getId());
        }
        return R.ok(shipOrders.load(order.getId()));
    }

    @PostMapping("/return")
    public R<Asn> inboundReturn(@RequestBody Map<String, Object> body) {
        String returnNo = first(text(body.get("returnNo")), text(body.get("orderNo")));
        if (returnNo == null) {
            throw new BizException("returnNo 不能为空");
        }
        Asn existing = asns.findByExternalNo(returnNo);
        if (existing != null) {
            return R.ok(existing);
        }
        Asn asn = new Asn();
        asn.setWarehouseCode(warehouse(text(body.get("warehouseCode"))));
        asn.setOwnerCode(first(text(body.get("ownerCode")), "OWN01"));
        asn.setType("RETURN");
        asn.setExternalNo(returnNo);
        asn.setCustomerCode(first(text(body.get("customerCode")), text(body.get("orderNo")), "OMS"));
        asn.setRemark("OMS 退货 " + returnNo);
        asn.setLines(returnLines(body.get("items")));
        return R.ok(asns.create(asn));
    }

    static String warehouse(String code) {
        if (code == null) {
            return "WH01";
        }
        String value = code.trim().toUpperCase();
        if ("WH-SH".equals(value) || "SH".equals(value)) {
            return "WH01";
        }
        if ("WH-BJ".equals(value) || "BJ".equals(value)) {
            return "WH02";
        }
        if ("WH-GZ".equals(value) || "GZ".equals(value)) {
            return "WH03";
        }
        return code.trim();
    }

    @SuppressWarnings("unchecked")
    private static List<ShipOrderLine> shipLines(Object raw) {
        List<ShipOrderLine> lines = new ArrayList<>();
        if (!(raw instanceof List)) {
            throw new BizException("出库单至少需要一行明细");
        }
        for (Object row : (List<?>) raw) {
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) row;
            ShipOrderLine line = new ShipOrderLine();
            line.setItemCode(first(text(item.get("sku")), text(item.get("itemCode"))));
            line.setOrderQty(qty(item.get("qty")));
            lines.add(line);
        }
        if (lines.isEmpty()) {
            throw new BizException("出库单至少需要一行明细");
        }
        return lines;
    }

    @SuppressWarnings("unchecked")
    private static List<AsnLine> returnLines(Object raw) {
        List<AsnLine> lines = new ArrayList<>();
        if (!(raw instanceof List)) {
            throw new BizException("退货单至少需要一行明细");
        }
        for (Object row : (List<?>) raw) {
            if (!(row instanceof Map)) {
                continue;
            }
            Map<String, Object> item = (Map<String, Object>) row;
            AsnLine line = new AsnLine();
            line.setItemCode(first(text(item.get("sku")), text(item.get("itemCode"))));
            line.setExpectedQty(qty(item.get("qty")));
            lines.add(line);
        }
        if (lines.isEmpty()) {
            throw new BizException("退货单至少需要一行明细");
        }
        return lines;
    }

    private static BigDecimal qty(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value != null && !String.valueOf(value).trim().isEmpty()) {
            return new BigDecimal(String.valueOf(value).trim());
        }
        throw new BizException("明细数量必须大于0");
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
}
