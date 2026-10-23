package com.wms.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.BizException;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
import com.wms.outbound.entity.ShipOrder;
import com.wms.outbound.entity.ShipOrderLine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 采购入库收完后把累计数量推给 SRM。默认 mock，不外呼。 */
@Component
public class SrmReceiptClient {
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${wms.srm.mode:mock}")
    private String mode;
    @Value("${wms.srm.base-url:http://localhost:8087}")
    private String baseUrl;
    @Value("${wms.srm.api-key:srm-wms-key}")
    private String apiKey;

    public void push(Asn asn) {
        post("/api/integration/wms/receipt", payload(asn), "收货");
    }

    public void pushSuggest(Asn asn) {
        post("/api/integration/wms/vmi-suggest", suggestPayload(asn), "VMI 建议");
    }

    public void pushRtv(ShipOrder order) {
        post("/api/integration/wms/rtv", rtvPayload(order), "退供");
    }

    private void post(String path, Map<String, Object> body, String name) {
        if (body == null || !"http".equalsIgnoreCase(mode)) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", apiKey);
        try {
            String response = rest.postForObject(baseUrl + path,
                    new HttpEntity<Object>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException("SRM 拒绝" + name + "回传: " + response);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("推送 SRM " + name + "失败: " + e.getMessage());
        }
    }

    /** 只回传已收完的采购入库。数量是累计值，退货和未完成的单据不推。 */
    public static Map<String, Object> payload(Asn asn) {
        if (asn == null || asn.getExternalNo() == null || asn.getExternalNo().trim().isEmpty()) {
            return null;
        }
        if (!"RECEIVED".equals(asn.getStatus()) || "RETURN".equals(asn.getType())) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("wmsAsnCode", asn.getCode());
        body.put("externalNo", asn.getExternalNo().trim());
        body.put("cumulative", true);
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        if (asn.getLines() != null) {
            for (AsnLine line : asn.getLines()) {
                if (line.getReceivedQty() == null || line.getReceivedQty().signum() <= 0) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("itemCode", line.getItemCode());
                item.put("lotNo", line.getLotNo());
                item.put("receivedQty", line.getReceivedQty());
                item.put("rejectedQty", line.getRejectedQty());
                lines.add(item);
            }
        }
        body.put("lines", lines);
        return body;
    }

    /** VMI 建议入库。不要求外部单号，WMS 不自动收货。 */
    public static Map<String, Object> suggestPayload(Asn asn) {
        if (asn == null || !"VMI_SUGGEST".equals(asn.getType())) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("wmsAsnCode", asn.getCode());
        body.put("supplierCode", asn.getSupplierCode());
        body.put("ownerCode", asn.getOwnerCode());
        body.put("warehouseCode", asn.getWarehouseCode());
        body.put("remark", asn.getRemark());
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        if (asn.getLines() != null) {
            for (AsnLine line : asn.getLines()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("itemCode", line.getItemCode());
                item.put("expectedQty", line.getExpectedQty());
                lines.add(item);
            }
        }
        body.put("lines", lines);
        return body;
    }

    /** 退供发运完成后回传。只接受已发运的 RTV。 */
    public static Map<String, Object> rtvPayload(ShipOrder order) {
        if (order == null || !"RTV".equals(order.getType()) || !"SHIPPED".equals(order.getStatus())) {
            return null;
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("wmsOrderCode", order.getCode());
        body.put("externalNo", order.getExternalNo());
        body.put("ownerCode", order.getOwnerCode());
        body.put("warehouseCode", order.getWarehouseCode());
        List<Map<String, Object>> lines = new ArrayList<Map<String, Object>>();
        if (order.getLines() != null) {
            for (ShipOrderLine line : order.getLines()) {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("itemCode", line.getItemCode());
                item.put("shippedQty", line.getShippedQty() == null ? line.getPickedQty() : line.getShippedQty());
                lines.add(item);
            }
        }
        body.put("lines", lines);
        return body;
    }
}
