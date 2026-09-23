package com.wms.integration.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.BizException;
import com.wms.inbound.entity.Asn;
import com.wms.inbound.entity.AsnLine;
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
        Map<String, Object> body = payload(asn);
        if (body == null || !"http".equalsIgnoreCase(mode)) {
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", apiKey);
        try {
            String response = rest.postForObject(baseUrl + "/api/integration/wms/receipt",
                    new HttpEntity<Object>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            if (root.path("code").asInt(-1) != 0) {
                throw new BizException("SRM 拒绝收货回传: " + response);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("推送 SRM 收货失败: " + e.getMessage());
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
}
